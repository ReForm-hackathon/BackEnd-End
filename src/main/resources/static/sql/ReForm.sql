-- 1. user
CREATE TABLE user (
    user_id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(255),
    nickname VARCHAR(255),
    password VARCHAR(255),
    role ENUM('USER','ADMIN'),
    refresh_token VARCHAR(255),
    address VARCHAR(255),
    provider VARCHAR(255), -- VARCHAR to allow LOCAL/others
    phone VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. product_tag
CREATE TABLE product_tag (
    product_tag_id INT AUTO_INCREMENT PRIMARY KEY,
    tag_name VARCHAR(255)
);

-- 3. my_item
CREATE TABLE my_item (
    item_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    product_tag_id INT,
    item_image VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES user(user_id),
    FOREIGN KEY (product_tag_id) REFERENCES product_tag(product_tag_id)
);

-- 4. community
CREATE TABLE community (
    community_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255),
    content TEXT,
    image VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 5. market
CREATE TABLE market (
    market_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255),
    content TEXT,
    image VARCHAR(255),
    price INT,
    is_donation BOOLEAN,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 6. community_like
CREATE TABLE community_like (
    community_id INT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    liked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (community_id, user_id),
    FOREIGN KEY (community_id) REFERENCES community(community_id),
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 7. market_like
CREATE TABLE market_like (
    market_id INT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    liked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (market_id, user_id),
    FOREIGN KEY (market_id) REFERENCES market(market_id),
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 8. community_comment
CREATE TABLE community_comment (
    comment_id INT AUTO_INCREMENT,
    community_id INT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    content VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comment_id, community_id, user_id),
    FOREIGN KEY (community_id) REFERENCES community(community_id),
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 9. market_comment
CREATE TABLE market_comment (
    comment_id INT AUTO_INCREMENT PRIMARY KEY,
    market_id INT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    content VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (market_id) REFERENCES market(market_id),
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 10. ai_chat_answer
CREATE TABLE ai_chat_answer (
    answer_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    item_id INT NOT NULL,
    recommendation TEXT,
    difficulty ENUM('easy','medium','hard'),
    required_tools TEXT,
    estimated_time VARCHAR(50),
    tutorial_link VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES user(user_id),
    FOREIGN KEY (item_id) REFERENCES my_item(item_id)
);

-- 11. chat
CREATE TABLE chat (
    chat_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    message VARCHAR(255),
    sender ENUM('USER', 'AI'),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 12. community_tag
CREATE TABLE community_tag (
    tag_id INT AUTO_INCREMENT PRIMARY KEY,
    community_id INT NOT NULL,
    tag_content VARCHAR(255),
    FOREIGN KEY (community_id) REFERENCES community(community_id)
);

-- 1. user
CREATE TABLE user (
                      user_id VARCHAR(255) PRIMARY KEY,
                      email VARCHAR(255),
                      name VARCHAR(255),
                      nickname VARCHAR(255),
                      password VARCHAR(255),
                      role ENUM('USER','ADMIN'),
                      refresh_token VARCHAR(255),
                      address VARCHAR(255),
                      provider VARCHAR(255),
                      phone VARCHAR(255),
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. product_tag
CREATE TABLE product_tag (
                             product_tag_id INT AUTO_INCREMENT PRIMARY KEY,
                             tag_name VARCHAR(255)
);

-- 3. my_item
CREATE TABLE my_item (
                         item_id INT AUTO_INCREMENT PRIMARY KEY,
                         user_id VARCHAR(255) NOT NULL,
                         product_tag_id INT,
                         item_image VARCHAR(255),
                         FOREIGN KEY (user_id) REFERENCES user(user_id),
                         FOREIGN KEY (product_tag_id) REFERENCES product_tag(product_tag_id)
);

-- 4. community
CREATE TABLE community (
                           community_id INT AUTO_INCREMENT PRIMARY KEY,
                           user_id VARCHAR(255) NOT NULL,
                           title VARCHAR(255),
                           content TEXT,
                           image VARCHAR(255),
                           created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                           FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 5. market
CREATE TABLE market (
                        market_id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id VARCHAR(255) NOT NULL,
                        title VARCHAR(255),
                        content TEXT,
                        image VARCHAR(255),
                        price INT,
                        is_donation BOOLEAN,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 6. community_like
CREATE TABLE community_like (
                                community_id INT NOT NULL,
                                user_id VARCHAR(255) NOT NULL,
                                liked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                PRIMARY KEY (community_id, user_id),
                                FOREIGN KEY (community_id) REFERENCES community(community_id),
                                FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 7. market_like
CREATE TABLE market_like (
                             market_id INT NOT NULL,
                             user_id VARCHAR(255) NOT NULL,
                             liked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                             PRIMARY KEY (market_id, user_id),
                             FOREIGN KEY (market_id) REFERENCES market(market_id),
                             FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 8. community_comment
CREATE TABLE community_comment (
                                   comment_id INT AUTO_INCREMENT,
                                   community_id INT NOT NULL,
                                   user_id VARCHAR(255) NOT NULL,
                                   content VARCHAR(255),
                                   created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                   PRIMARY KEY (comment_id, community_id, user_id),
                                   FOREIGN KEY (community_id) REFERENCES community(community_id),
                                   FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 9. market_comment
CREATE TABLE market_comment (
                                comment_id INT AUTO_INCREMENT PRIMARY KEY,
                                market_id INT NOT NULL,
                                user_id VARCHAR(255) NOT NULL,
                                content VARCHAR(255),
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (market_id) REFERENCES market(market_id),
                                FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 10. ai_chat_answer
CREATE TABLE ai_chat_answer (
                                answer_id INT AUTO_INCREMENT PRIMARY KEY,
                                user_id VARCHAR(255) NOT NULL,
                                item_id INT NOT NULL,
                                recommendation TEXT,
                                difficulty ENUM('easy','medium','hard'),
                                required_tools TEXT,
                                estimated_time VARCHAR(50),
                                tutorial_link VARCHAR(255),
                                FOREIGN KEY (user_id) REFERENCES user(user_id),
                                FOREIGN KEY (item_id) REFERENCES my_item(item_id)
);

-- 11. chat
CREATE TABLE chat (
                      chat_id INT AUTO_INCREMENT PRIMARY KEY,
                      user_id VARCHAR(255) NOT NULL,
                      message VARCHAR(255),
                      sender ENUM('USER', 'AI'),
                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                      FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 12. community_tag
CREATE TABLE community_tag (
                               tag_id INT AUTO_INCREMENT PRIMARY KEY,
                               community_id INT NOT NULL,
                               tag_content VARCHAR(255),
                               FOREIGN KEY (community_id) REFERENCES community(community_id)
);
