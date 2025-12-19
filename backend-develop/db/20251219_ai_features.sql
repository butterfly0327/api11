-- AI 및 통계 신규 테이블

-- Gemini 식단 추천 결과
CREATE TABLE IF NOT EXISTS ai_meal_plans (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    plan_date DATE NOT NULL,
    prompt_context TEXT DEFAULT NULL,
    raw_response LONGTEXT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_meal_plans_email_date (email, plan_date),
    CONSTRAINT fk_ai_meal_plans_account FOREIGN KEY (email) REFERENCES accounts(email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ai_meal_plan_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    meal_plan_id BIGINT UNSIGNED NOT NULL,
    meal_time VARCHAR(50) DEFAULT NULL,
    menu_description TEXT NOT NULL,
    calories DOUBLE DEFAULT NULL,
    highlight VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_meal_plan_items_plan (meal_plan_id),
    CONSTRAINT fk_ai_meal_plan_items_plan FOREIGN KEY (meal_plan_id) REFERENCES ai_meal_plans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Gemini 챗봇 대화 내역
CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    message_date DATE NOT NULL,
    sender VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_chat_messages_email_date (email, message_date, created_at),
    CONSTRAINT fk_ai_chat_messages_account FOREIGN KEY (email) REFERENCES accounts(email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 주간 영양 평가 결과
CREATE TABLE IF NOT EXISTS ai_nutrition_reviews (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    week_start_date DATE NOT NULL,
    evaluation_date DATE NOT NULL,
    summary TEXT NOT NULL,
    carbohydrate_status VARCHAR(50) DEFAULT NULL,
    protein_status VARCHAR(50) DEFAULT NULL,
    fat_status VARCHAR(50) DEFAULT NULL,
    calorie_status VARCHAR(50) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_nutrition_reviews_email_eval (email, evaluation_date),
    CONSTRAINT fk_ai_nutrition_reviews_account FOREIGN KEY (email) REFERENCES accounts(email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 주간 운동 평가 결과
CREATE TABLE IF NOT EXISTS ai_exercise_reviews (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    week_start_date DATE NOT NULL,
    evaluation_date DATE NOT NULL,
    summary TEXT NOT NULL,
    recommendation TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_exercise_reviews_email_eval (email, evaluation_date),
    CONSTRAINT fk_ai_exercise_reviews_account FOREIGN KEY (email) REFERENCES accounts(email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
