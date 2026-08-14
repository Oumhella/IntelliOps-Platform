CREATE TABLE conversation_messages (id bigserial PRIMARY KEY,enterprise_id bigint NOT NULL,user_id varchar(100) NOT NULL,surface varchar(20) NOT NULL CHECK(surface IN('ASSISTANT','BI')),role varchar(20) NOT NULL CHECK(role IN('user','assistant')),content text NOT NULL CHECK(char_length(content)<=12000),payload jsonb,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX idx_conversation_owner ON conversation_messages(enterprise_id,user_id,surface,created_at DESC);
GRANT SELECT,INSERT,DELETE ON conversation_messages TO analytics_sync;
GRANT USAGE,SELECT ON SEQUENCE conversation_messages_id_seq TO analytics_sync;
