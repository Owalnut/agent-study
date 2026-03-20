CREATE TABLE IF NOT EXISTS workflow_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    workflow_json JSON NOT NULL,
    is_draft TINYINT NOT NULL DEFAULT 1,
    is_published TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workflow_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    input_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    output_text TEXT NULL,
    audio_base64 LONGTEXT NULL,
    node_results LONGTEXT NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- MySQL does not support `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, so we make it idempotent:
SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'workflow_execution'
    AND column_name = 'node_results'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE workflow_execution ADD COLUMN node_results LONGTEXT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
