package org.example.lead_service.service;

import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.PageResponse;
import org.example.common.exception.ResourceNotFoundException;
import org.example.common.security.TenantContext;
import org.example.lead_service.client.StockClient;
import org.example.lead_service.dto.AddOrderLineRequest;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.dto.StockInventoryDTO;
import org.example.lead_service.dto.StockProductDTO;
import org.example.lead_service.entity.Commande;
import org.example.lead_service.entity.StatutCommande;
import org.example.lead_service.entity.StatutPaiementCommande;
import org.example.lead_service.mapper.CommandeMapper;
import org.example.lead_service.repository.CommandeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommandeServiceImpl implements CommandeService {

    private final CommandeRepository commandeRepository;
    private final CommandeMapper commandeMapper;
    private final StockClient stockClient;

    @Override
    @Transactional(readOnly = true)
    public CommandeDTO obtenirCommandeParId(Long idCommande) {
        return commandeMapper.toDto(findOrder(idCommande));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommandeDTO> rechercherCommandes(StatutCommande statut, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "idCommande"));
        return PageResponse.from(
                commandeRepository.search(TenantContext.requireEnterpriseId(), statut, pageable),
                commandeMapper::toDto);
    }

    @Override
    public CommandeDTO ajouterProduitACommande(Long idCommande, AddOrderLineRequest request) {
        Commande order = findOrder(idCommande);
        if (order.getStatutCommande() != StatutCommande.EN_ATTENTE) {
            throw new IllegalStateException("Order lines can only be edited while the order is pending.");
        }
        if (!TenantContext.requireUserId().equals(order.getLead().getAgentId())) {
            throw new AccessDeniedException(
                    "Only the CSM assigned to the originating lead can edit this order.");
        }
        requireReservableOrder(order);
        if (order.getLignesCommande().stream()
                .anyMatch(line -> line.getProduitId().equals(request.productId()))) {
            throw new IllegalStateException("This product already exists in the order.");
        }

        StockProductDTO product;
        try {
            product = stockClient.obtenirProduit(request.productId());
            StockInventoryDTO inventory = stockClient.obtenirInventaire(
                    order.getStockLocationId(), request.productId());
            if (inventory == null || inventory.getQuantiteDisponible() < request.quantity()) {
                throw new IllegalStateException("Insufficient available stock for this product.");
            }
        } catch (FeignException exception) {
            throw new ResourceNotFoundException("Product or inventory not found.");
        }
        if (product == null || product.getPrixVente() <= 0) {
            throw new IllegalStateException("The product has no valid sale price.");
        }

        StockClient.ReservationRequest reservation = new StockClient.ReservationRequest(
                request.quantity(), order.getStockReservationReference());
        stockClient.reserverStock(order.getStockLocationId(), request.productId(), reservation);
        try {
            order.ajouterLigne(request.productId(), request.quantity(), product.getPrixVente());
            return commandeMapper.toDto(commandeRepository.save(order));
        } catch (RuntimeException exception) {
            stockClient.libererStock(order.getStockLocationId(), request.productId(), reservation);
            throw exception;
        }
    }

    @Override
    public CommandeDTO changerStatutCommande(Long idCommande, StatutCommande nouveauStatut) {
        Commande order = findOrder(idCommande);
        if (order.getStatutCommande() == nouveauStatut) {
            return commandeMapper.toDto(order);
        }
        if (!isAllowedTransition(order.getStatutCommande(), nouveauStatut)) {
            throw new IllegalStateException(
                    "Invalid order transition from " + order.getStatutCommande() + " to " + nouveauStatut + ".");
        }

        if (nouveauStatut == StatutCommande.PREPARATION
                && order.getStatutPaiement() != StatutPaiementCommande.PAID
                && order.getStatutPaiement() != StatutPaiementCommande.AWAITING_COLLECTION) {
            throw new IllegalStateException(
                    "An order must be paid or configured for cash on delivery before preparation.");
        }

        if (nouveauStatut == StatutCommande.ANNULEE) {
            releaseOrderReservations(order);
        } else if (nouveauStatut == StatutCommande.EXPEDIEE) {
            consumeOrderReservations(order);
        }

        order.changerStatut(nouveauStatut);
        return commandeMapper.toDto(commandeRepository.save(order));
    }

    @Override
    public CommandeDTO changerStatutPaiement(Long idCommande, StatutPaiementCommande nouveauStatut) {
        Commande order = findOrder(idCommande);
        StatutPaiementCommande current = order.getStatutPaiement();
        if (current == nouveauStatut) {
            return commandeMapper.toDto(order);
        }
        boolean allowed = switch (current) {
            case UNPAID -> nouveauStatut == StatutPaiementCommande.AWAITING_COLLECTION
                    || nouveauStatut == StatutPaiementCommande.PAID;
            case AWAITING_COLLECTION -> nouveauStatut == StatutPaiementCommande.PAID
                    || nouveauStatut == StatutPaiementCommande.UNPAID;
            case PAID -> nouveauStatut == StatutPaiementCommande.PARTIALLY_REFUNDED
                    || nouveauStatut == StatutPaiementCommande.REFUNDED;
            case PARTIALLY_REFUNDED -> nouveauStatut == StatutPaiementCommande.REFUNDED;
            case REFUNDED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException(
                    "Invalid order payment transition from " + current + " to " + nouveauStatut + ".");
        }
        order.setStatutPaiement(nouveauStatut);
        return commandeMapper.toDto(commandeRepository.save(order));
    }

    private void releaseOrderReservations(Commande order) {
        requireReservableOrder(order);
        order.getLignesCommande().forEach(line -> stockClient.libererStock(
                order.getStockLocationId(), line.getProduitId(),
                new StockClient.ReservationRequest(
                        line.getQuantite(), order.getStockReservationReference())));
    }

    private void consumeOrderReservations(Commande order) {
        requireReservableOrder(order);
        order.getLignesCommande().forEach(line -> stockClient.consommerStock(
                order.getStockLocationId(), line.getProduitId(),
                new StockClient.ReservationRequest(
                        line.getQuantite(), order.getStockReservationReference())));
    }

    private void requireReservableOrder(Commande order) {
        if (order.getStockLocationId() == null || order.getStockReservationReference() == null) {
            throw new IllegalStateException(
                    "This legacy order has no fulfillment location or stock reservation reference.");
        }
    }

    private Commande findOrder(Long idCommande) {
        return commandeRepository.findByIdCommandeAndLeadEnterpriseId(
                        idCommande, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + idCommande));
    }

    private boolean isAllowedTransition(StatutCommande current, StatutCommande next) {
        if (current == next) return false;
        return switch (current) {
            case EN_ATTENTE -> next == StatutCommande.CONFIRMEE || next == StatutCommande.ANNULEE;
            case CONFIRMEE -> next == StatutCommande.PREPARATION || next == StatutCommande.ANNULEE;
            case PREPARATION -> next == StatutCommande.EXPEDIEE || next == StatutCommande.ANNULEE;
            case EXPEDIEE -> next == StatutCommande.LIVREE || next == StatutCommande.RETOURNEE;
            case LIVREE -> next == StatutCommande.RETOURNEE;
            case ANNULEE, RETOURNEE -> false;
        };
    }
}
