from collections.abc import Iterable
from datetime import datetime

from app.db import connection


def extract_orders(database_url: str, after: datetime | None, limit: int) -> Iterable[dict]:
    with connection(database_url, readonly=True) as conn:
        yield from conn.execute(
            """
            SELECT c.id_commande AS order_id, l.enterprise_id, c.reference,
                   c.stock_location_id AS store_id, c.statut_commande AS status,
                   c.statut_paiement AS payment_status, c.total_prix AS total_amount,
                   c.created_at AS source_updated_at
            FROM commandes c JOIN leads l ON l.id_lead = c.lead_id
            WHERE (%(after)s::timestamp IS NULL OR c.created_at > %(after)s::timestamp)
            ORDER BY c.created_at, c.id_commande LIMIT %(limit)s
            """,
            {"after": after, "limit": limit},
        )


def extract_order_lines(database_url: str, order_ids: list[int]) -> Iterable[dict]:
    if not order_ids:
        return
    with connection(database_url, readonly=True) as conn:
        yield from conn.execute(
            """
            SELECT lc.id_ligne AS order_line_id, lc.commande_id AS order_id,
                   l.enterprise_id, lc.produit_id AS product_id, lc.quantite AS quantity,
                   lc.prix_unitaire_applique AS unit_price
            FROM lignes_commande lc
            JOIN commandes c ON c.id_commande = lc.commande_id
            JOIN leads l ON l.id_lead = c.lead_id
            WHERE lc.commande_id = ANY(%s)
            """,
            (order_ids,),
        )


def extract_stock(database_url: str) -> tuple[list[dict], list[dict], list[dict]]:
    with connection(database_url, readonly=True) as conn:
        products = list(
            conn.execute(
                "SELECT id_produit AS product_id, enterprise_id, global_sku, "
                "nom_produit AS name, prix_achat AS purchase_price, prix_vente AS sale_price "
                "FROM produits"
            )
        )
        stores = list(
            conn.execute(
                "SELECT id_boutique AS store_id, enterprise_id, nom_boutique AS name, "
                "plateforme_type AS platform FROM boutiques"
            )
        )
        inventory = list(
            conn.execute(
                """
            SELECT i.id AS inventory_id, p.enterprise_id, i.boutique_id AS store_id,
                   i.produit_id AS product_id, i.quantite_disponible AS available_quantity,
                   i.quantite_reservee AS reserved_quantity,
                   r.seuil_alerte AS alert_threshold
            FROM inventaires i
            JOIN produits p ON p.id_produit = i.produit_id
            JOIN boutiques b ON b.id_boutique = i.boutique_id
            LEFT JOIN regles_approvisionnement r ON r.id = i.regle_approvisionnement_id
            WHERE b.enterprise_id = p.enterprise_id
            """
            )
        )
    return products, stores, inventory
