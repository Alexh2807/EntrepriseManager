-- ========================================
-- SCHEMA COMPLET ROLEPLAYCITY
-- Une base SQLite unique pour TOUT le plugin
-- ========================================

-- ========================================
-- SECTION 1: ENTREPRISES (D\u00e9j\u00e0 impl\u00e9ment\u00e9)
-- ========================================

CREATE TABLE IF NOT EXISTS entreprises (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    siret TEXT UNIQUE NOT NULL,
    nom TEXT UNIQUE NOT NULL,
    ville TEXT NOT NULL,
    description TEXT,
    type TEXT NOT NULL,
    gerant_nom TEXT NOT NULL,
    gerant_uuid TEXT NOT NULL,
    capital REAL DEFAULT 0.0,
    solde REAL DEFAULT 0.0,
    chiffre_affaires_total REAL DEFAULT 0.0,
    niveau_max_employes INTEGER DEFAULT 0,
    niveau_max_solde INTEGER DEFAULT 0,
    niveau_restrictions INTEGER DEFAULT 0,
    total_value REAL DEFAULT 0.0,
    creation_date TEXT,
    created_at INTEGER,
    updated_at INTEGER,
    is_dirty INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS employes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entreprise_siret TEXT NOT NULL,
    employe_uuid TEXT NOT NULL,
    employe_nom TEXT NOT NULL,
    poste TEXT DEFAULT 'EMPLOYE',
    salaire REAL DEFAULT 0.0,
    join_date INTEGER,
    total_production_value REAL DEFAULT 0.0,
    total_salary_paid REAL DEFAULT 0.0,
    total_bonus_paid REAL DEFAULT 0.0,
    prime REAL DEFAULT 0.0,
    FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE,
    UNIQUE(entreprise_siret, employe_uuid)
);

CREATE INDEX IF NOT EXISTS idx_employes_entreprise ON employes(entreprise_siret);
CREATE INDEX IF NOT EXISTS idx_employes_uuid ON employes(employe_uuid);

CREATE TABLE IF NOT EXISTS employee_activities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entreprise_siret TEXT NOT NULL,
    employee_uuid TEXT NOT NULL,
    employee_name TEXT NOT NULL,
    current_session_start INTEGER,
    last_activity_time INTEGER,
    total_value_generated REAL DEFAULT 0.0,
    join_date INTEGER,
    FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE,
    UNIQUE(entreprise_siret, employee_uuid)
);

CREATE INDEX IF NOT EXISTS idx_activities_entreprise ON employee_activities(entreprise_siret);

CREATE TABLE IF NOT EXISTS employee_actions (
    activity_id INTEGER NOT NULL,
    action_key TEXT NOT NULL,
    count INTEGER DEFAULT 0,
    FOREIGN KEY (activity_id) REFERENCES employee_activities(id) ON DELETE CASCADE,
    PRIMARY KEY (activity_id, action_key)
);

CREATE TABLE IF NOT EXISTS detailed_production (
    activity_id INTEGER NOT NULL,
    timestamp INTEGER NOT NULL,
    action_type TEXT NOT NULL,
    material TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    FOREIGN KEY (activity_id) REFERENCES employee_activities(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_detailed_prod_activity ON detailed_production(activity_id);

CREATE TABLE IF NOT EXISTS global_production (
    entreprise_siret TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    material TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    employee_uuid TEXT NOT NULL,
    action_type TEXT NOT NULL,
    FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_global_prod_entreprise ON global_production(entreprise_siret);

CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entreprise_siret TEXT NOT NULL,
    type TEXT NOT NULL,
    amount REAL NOT NULL,
    description TEXT,
    initiated_by TEXT,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transactions_entreprise ON transactions(entreprise_siret);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(timestamp);

CREATE TABLE IF NOT EXISTS production_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entreprise_siret TEXT NOT NULL,
    item_type TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    total_value REAL NOT NULL,
    date TEXT NOT NULL,
    FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_production_entreprise ON production_log(entreprise_siret);

-- ========================================
-- SECTION 2: VILLES ET PARCELLES
-- ========================================

CREATE TABLE IF NOT EXISTS towns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    description TEXT,
    mayor_uuid TEXT NOT NULL,
    foundation_date TEXT NOT NULL,
    level TEXT NOT NULL DEFAULT 'VILLAGE',
    bank_balance REAL DEFAULT 0.0,
    citizen_tax REAL DEFAULT 0.0,
    company_tax REAL DEFAULT 0.0,
    last_tax_collection TEXT NOT NULL,
    spawn_world TEXT,
    spawn_x REAL,
    spawn_y REAL,
    spawn_z REAL,
    spawn_yaw REAL,
    spawn_pitch REAL
);

CREATE TABLE IF NOT EXISTS plots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    town_name TEXT NOT NULL,
    world TEXT NOT NULL,
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    plot_type TEXT DEFAULT 'UNCLAIMED',
    municipal_subtype TEXT DEFAULT 'NONE',
    plot_number TEXT,
    claim_date TEXT,
    
    owner_uuid TEXT,
    owner_name TEXT,
    
    -- Company & Debt
    company_siret TEXT,
    company_name TEXT,
    debt_amount REAL DEFAULT 0.0,
    debt_warning_count INTEGER DEFAULT 0,
    last_debt_warning TEXT,
    
    -- Particular Debt
    particular_debt_amount REAL DEFAULT 0.0,
    particular_debt_warning_count INTEGER DEFAULT 0,
    particular_last_debt_warning TEXT,
    
    price REAL DEFAULT 0.0,
    for_sale INTEGER DEFAULT 0,
    
    -- Rent
    rent_amount REAL DEFAULT 0.0,
    for_rent INTEGER DEFAULT 0,
    renter_uuid TEXT,
    renter_name TEXT,
    rent_start_date TEXT,
    rent_end_date TEXT,
    renter_company_siret TEXT,
    
    plot_group_id TEXT,
    
    -- Prison Spawn
    prison_spawn_world TEXT,
    prison_spawn_x REAL,
    prison_spawn_y REAL,
    prison_spawn_z REAL,
    prison_spawn_yaw REAL,
    prison_spawn_pitch REAL,
    
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE,
    UNIQUE(world, chunk_x, chunk_z)
);

CREATE INDEX IF NOT EXISTS idx_plots_town ON plots(town_name);
CREATE INDEX IF NOT EXISTS idx_plots_owner ON plots(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_plots_renter ON plots(renter_uuid);
CREATE INDEX IF NOT EXISTS idx_plots_location ON plots(world, chunk_x, chunk_z);
CREATE INDEX IF NOT EXISTS idx_plots_group ON plots(plot_group_id);

CREATE TABLE IF NOT EXISTS town_members (
    town_name TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    join_date TEXT NOT NULL,
    roles TEXT NOT NULL,
    PRIMARY KEY (town_name, player_uuid),
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_town_members_player ON town_members(player_uuid);

CREATE TABLE IF NOT EXISTS town_invites (
    town_name TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    invite_date TEXT NOT NULL,
    PRIMARY KEY (town_name, player_uuid),
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plot_flags (
    plot_id INTEGER NOT NULL,
    flag_name TEXT NOT NULL,
    value INTEGER DEFAULT 0,
    PRIMARY KEY (plot_id, flag_name),
    FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plot_permissions (
    plot_id INTEGER NOT NULL,
    player_uuid TEXT NOT NULL,
    permission TEXT NOT NULL,
    FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plot_trusted (
    plot_id INTEGER NOT NULL,
    player_uuid TEXT NOT NULL,
    FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
);

-- Autorisations parentales (propriétaire/locataire -> joueurs autorisés)
CREATE TABLE IF NOT EXISTS plot_authorizations (
    plot_id INTEGER NOT NULL,
    player_uuid TEXT NOT NULL,
    authorization_type TEXT NOT NULL, -- 'OWNER' ou 'RENTER'
    FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_plot_authorizations_plot ON plot_authorizations(plot_id);

CREATE TABLE IF NOT EXISTS plot_protected_blocks (
    plot_id INTEGER NOT NULL,
    world TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plot_renter_blocks (
    plot_id INTEGER NOT NULL,
    renter_uuid TEXT NOT NULL,
    world TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_renter_blocks_plot ON plot_renter_blocks(plot_id);

CREATE TABLE IF NOT EXISTS plot_groups (
    group_id TEXT PRIMARY KEY,
    town_name TEXT NOT NULL,
    owner_uuid TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    company_siret TEXT,
    company_name TEXT,
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE,
    FOREIGN KEY (company_siret) REFERENCES entreprises(siret) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_plot_groups_town ON plot_groups(town_name);
CREATE INDEX IF NOT EXISTS idx_plot_groups_owner ON plot_groups(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_plot_groups_company ON plot_groups(company_siret);

-- ========================================
-- SECTION 3: BOUTIQUES
-- ========================================

CREATE TABLE IF NOT EXISTS shops (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_uuid TEXT UNIQUE NOT NULL,
    entreprise_siret TEXT NOT NULL,
    entreprise_name TEXT NOT NULL,
    owner_uuid TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    
    -- Locations
    chest_world TEXT NOT NULL,
    chest_x REAL NOT NULL,
    chest_y REAL NOT NULL,
    chest_z REAL NOT NULL,
    
    sign_world TEXT NOT NULL,
    sign_x REAL NOT NULL,
    sign_y REAL NOT NULL,
    sign_z REAL NOT NULL,
    
    hologram_world TEXT,
    hologram_x REAL,
    hologram_y REAL,
    hologram_z REAL,
    
    -- Data
    item_template TEXT NOT NULL,
    quantity_per_sale INTEGER DEFAULT 1,
    price_per_sale REAL DEFAULT 0.0,
    
    -- Stats
    creation_date TEXT NOT NULL,
    last_activity TEXT,
    last_stock_check TEXT,
    last_purchase TEXT,
    total_sales INTEGER DEFAULT 0,
    total_items_sold INTEGER DEFAULT 0,
    total_revenue REAL DEFAULT 0.0,
    cached_stock INTEGER DEFAULT 0,

    -- Status & Entities
    status TEXT DEFAULT 'ACTIVE',
    display_item_entity_id TEXT,
    hologram_entity_ids TEXT
);

CREATE INDEX IF NOT EXISTS idx_shops_owner ON shops(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_shops_entreprise ON shops(entreprise_siret);

CREATE TABLE IF NOT EXISTS shop_top_buyers (
    shop_uuid TEXT NOT NULL,
    buyer_name TEXT NOT NULL,
    count INTEGER DEFAULT 1,
    PRIMARY KEY (shop_uuid, buyer_name),
    FOREIGN KEY (shop_uuid) REFERENCES shops(shop_uuid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS shop_stock (
    shop_id INTEGER NOT NULL,
    material TEXT NOT NULL,
    quantity INTEGER DEFAULT 0,
    buy_price REAL,
    sell_price REAL,
    last_restock TEXT,
    PRIMARY KEY (shop_id, material),
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_shop_stock_material ON shop_stock(material);

-- ========================================
-- SECTION 4: MODE SERVICE
-- ========================================

CREATE TABLE IF NOT EXISTS service_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    entreprise_siret TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT,
    total_earned REAL DEFAULT 0.0,
    is_active INTEGER DEFAULT 1,
    FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_service_player ON service_sessions(player_uuid);
CREATE INDEX IF NOT EXISTS idx_service_entreprise ON service_sessions(entreprise_siret);
CREATE INDEX IF NOT EXISTS idx_service_active ON service_sessions(is_active);

CREATE TABLE IF NOT EXISTS service_earnings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL,
    timestamp TEXT NOT NULL,
    amount REAL NOT NULL,
    description TEXT,
    FOREIGN KEY (session_id) REFERENCES service_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_earnings_session ON service_earnings(session_id);

-- ========================================
-- SECTION 5: SYST\u00c8ME M\u00c9DICAL
-- ========================================

CREATE TABLE IF NOT EXISTS injured_players (
    player_uuid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    injury_time TEXT NOT NULL,
    injury_type TEXT NOT NULL,
    location_world TEXT,
    location_x REAL,
    location_y REAL,
    location_z REAL,
    treated INTEGER DEFAULT 0,
    medic_uuid TEXT,
    medic_name TEXT,
    treatment_time TEXT
);

CREATE INDEX IF NOT EXISTS idx_injured_treated ON injured_players(treated);

CREATE TABLE IF NOT EXISTS medical_treatments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patient_uuid TEXT NOT NULL,
    patient_name TEXT NOT NULL,
    medic_uuid TEXT NOT NULL,
    medic_name TEXT NOT NULL,
    treatment_time TEXT NOT NULL,
    treatment_type TEXT NOT NULL,
    cost REAL DEFAULT 0.0,
    town_name TEXT,
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_treatments_patient ON medical_treatments(patient_uuid);
CREATE INDEX IF NOT EXISTS idx_treatments_medic ON medical_treatments(medic_uuid);
CREATE INDEX IF NOT EXISTS idx_treatments_town ON medical_treatments(town_name);

-- ========================================
-- SECTION 6: AMENDES
-- ========================================

CREATE TABLE IF NOT EXISTS fines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fine_id TEXT UNIQUE NOT NULL,
    town_name TEXT NOT NULL,
    offender_uuid TEXT NOT NULL,
    offender_name TEXT NOT NULL,
    policier_uuid TEXT NOT NULL,
    policier_name TEXT NOT NULL,
    reason TEXT NOT NULL,
    amount REAL NOT NULL,
    issue_date TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'UNPAID',
    paid_date TEXT,
    contested_date TEXT,
    contest_reason TEXT,
    judge_uuid TEXT,
    judge_verdict TEXT,
    judge_date TEXT,
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_fines_town ON fines(town_name);
CREATE INDEX IF NOT EXISTS idx_fines_offender ON fines(offender_uuid);
CREATE INDEX IF NOT EXISTS idx_fines_status ON fines(status);

-- ========================================
-- SECTION 7: SACS \u00c0 DOS
-- ========================================

CREATE TABLE IF NOT EXISTS backpacks (
    backpack_id TEXT PRIMARY KEY,
    owner_uuid TEXT,
    backpack_type TEXT NOT NULL,
    backpack_data TEXT NOT NULL,
    last_update TEXT NOT NULL,
    size INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_backpacks_owner ON backpacks(owner_uuid);

-- ========================================
-- SECTION 8: NOTIFICATIONS
-- ========================================

CREATE TABLE IF NOT EXISTS notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    is_read INTEGER DEFAULT 0,
    read_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_notifications_player ON notifications(player_uuid);
CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications(is_read);

-- ========================================
-- SECTION 9: SYST\u00c8ME POLICE / PRISON
-- ========================================

CREATE TABLE IF NOT EXISTS prisons (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    town_name TEXT UNIQUE NOT NULL,
    spawn_world TEXT NOT NULL,
    spawn_x REAL NOT NULL,
    spawn_y REAL NOT NULL,
    spawn_z REAL NOT NULL,
    spawn_yaw REAL DEFAULT 0.0,
    spawn_pitch REAL DEFAULT 0.0,
    boundary_min_x INTEGER,
    boundary_min_z INTEGER,
    boundary_max_x INTEGER,
    boundary_max_z INTEGER,
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS imprisoned_players (
    player_uuid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    town_name TEXT NOT NULL,
    plot_identifier TEXT,
    reason TEXT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    arresting_officer_uuid TEXT NOT NULL,
    arresting_officer_name TEXT NOT NULL,
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_imprisoned_town ON imprisoned_players(town_name);

CREATE TABLE IF NOT EXISTS handcuffed_players (
    player_uuid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    officer_uuid TEXT NOT NULL,
    officer_name TEXT NOT NULL,
    handcuff_time TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tased_players (
    player_uuid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    officer_uuid TEXT NOT NULL,
    officer_name TEXT NOT NULL,
    tase_time TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL
);

-- ========================================
-- SECTION 10: POSTAL / BO\u00ceTES AUX LETTRES
-- ========================================

CREATE TABLE IF NOT EXISTS mailboxes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plot_group_id TEXT UNIQUE NOT NULL,
    town_name TEXT NOT NULL,
    world TEXT NOT NULL,
    x REAL NOT NULL,
    y REAL NOT NULL,
    z REAL NOT NULL,
    mailbox_type TEXT NOT NULL,
    creation_date TEXT NOT NULL,
    FOREIGN KEY (plot_group_id) REFERENCES plot_groups(group_id) ON DELETE CASCADE,
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mailboxes_town ON mailboxes(town_name);
CREATE INDEX IF NOT EXISTS idx_mailboxes_plot ON mailboxes(plot_group_id);
CREATE INDEX IF NOT EXISTS idx_mailboxes_location ON mailboxes(world, x, y, z);

CREATE TABLE IF NOT EXISTS mail_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mailbox_id INTEGER NOT NULL,
    sender_uuid TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    recipient_uuid TEXT NOT NULL,
    recipient_name TEXT NOT NULL,
    item_data TEXT NOT NULL,
    send_date TEXT NOT NULL,
    retrieved INTEGER DEFAULT 0,
    retrieve_date TEXT,
    FOREIGN KEY (mailbox_id) REFERENCES mailboxes(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mail_mailbox ON mail_items(mailbox_id);
CREATE INDEX IF NOT EXISTS idx_mail_recipient ON mail_items(recipient_uuid);
CREATE INDEX IF NOT EXISTS idx_mail_retrieved ON mail_items(retrieved);

-- ========================================
-- SECTION 11: CV (CURRICULUM VITAE)
-- ========================================

CREATE TABLE IF NOT EXISTS player_cvs (
    player_uuid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    last_update TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS cv_past_experiences (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    entreprise_name TEXT NOT NULL,
    poste TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT,
    description TEXT,
    FOREIGN KEY (player_uuid) REFERENCES player_cvs(player_uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cv_experiences_player ON cv_past_experiences(player_uuid);

-- ========================================
-- SECTION 12: CONTRATS
-- ========================================

CREATE TABLE IF NOT EXISTS contracts (
    id VARCHAR(36) PRIMARY KEY,
    service_id VARCHAR(255),
    provider_company VARCHAR(255) NOT NULL,
    provider_owner_uuid VARCHAR(36) NOT NULL,
    contract_type VARCHAR(10) NOT NULL, -- B2C ou B2B
    client_uuid VARCHAR(36),
    client_company VARCHAR(255),
    client_owner_uuid VARCHAR(36),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    amount DOUBLE NOT NULL,
    status VARCHAR(50) NOT NULL,
    funds_escrowed BOOLEAN DEFAULT 0,
    proposal_date VARCHAR(30) NOT NULL,
    expiration_date VARCHAR(30) NOT NULL,
    response_date VARCHAR(30),
    end_date VARCHAR(30),
    judge_uuid VARCHAR(36),
    dispute_reason TEXT,
    dispute_verdict TEXT
);

CREATE INDEX IF NOT EXISTS idx_contracts_provider ON contracts(provider_company);
CREATE INDEX IF NOT EXISTS idx_contracts_provider_owner ON contracts(provider_owner_uuid);
CREATE INDEX IF NOT EXISTS idx_contracts_client_uuid ON contracts(client_uuid);
CREATE INDEX IF NOT EXISTS idx_contracts_client_company ON contracts(client_company);
CREATE INDEX IF NOT EXISTS idx_contracts_client_owner ON contracts(client_owner_uuid);
CREATE INDEX IF NOT EXISTS idx_contracts_status ON contracts(status);
CREATE INDEX IF NOT EXISTS idx_contracts_type ON contracts(contract_type);

-- ========================================
-- SECTION 13: RENDEZ-VOUS MAIRIE
-- ========================================

CREATE TABLE IF NOT EXISTS appointments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    appointment_id TEXT UNIQUE NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    town_name TEXT NOT NULL,
    subject TEXT NOT NULL,
    request_date TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    treated_by_uuid TEXT,
    treated_by_name TEXT,
    treated_date TEXT,
    FOREIGN KEY (town_name) REFERENCES towns(name) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_appointments_town ON appointments(town_name);
CREATE INDEX IF NOT EXISTS idx_appointments_player ON appointments(player_uuid);
CREATE INDEX IF NOT EXISTS idx_appointments_status ON appointments(status);

-- ========================================
-- SECTION 14: IDENTITES (VILLE RESIDENCE)
-- ========================================

CREATE TABLE IF NOT EXISTS identities (
    player_uuid TEXT PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    sex TEXT NOT NULL,
    age INTEGER NOT NULL,
    height INTEGER NOT NULL,
    creation_date INTEGER NOT NULL,
    residence_city TEXT
);

CREATE INDEX IF NOT EXISTS idx_identities_residence ON identities(residence_city);

-- ========================================
-- SECTION 15: M\u00c9TADATA
-- ========================================

CREATE TABLE IF NOT EXISTS metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- Version du sch\u00e9ma
INSERT OR REPLACE INTO metadata (key, value) VALUES ('schema_version', '1.0');
INSERT OR REPLACE INTO metadata (key, value) VALUES ('plugin_version', '1.06.10');
INSERT OR REPLACE INTO metadata (key, value) VALUES ('created_at', datetime('now'));

-- ========================================
-- SECTION 16: SYSTEME TELEPHONIQUE
-- ========================================

-- Comptes telephone (Cloud - lie au joueur)
CREATE TABLE IF NOT EXISTS phone_accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_uuid TEXT UNIQUE NOT NULL,
    owner_name TEXT NOT NULL,
    phone_number TEXT UNIQUE NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_phone_owner ON phone_accounts(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_phone_number ON phone_accounts(phone_number);

-- Contacts (Cloud - lie au joueur)
CREATE TABLE IF NOT EXISTS phone_contacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_uuid TEXT NOT NULL,
    contact_name TEXT NOT NULL,
    contact_number TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (owner_uuid) REFERENCES phone_accounts(owner_uuid) ON DELETE CASCADE,
    UNIQUE(owner_uuid, contact_number)
);

CREATE INDEX IF NOT EXISTS idx_contacts_owner ON phone_contacts(owner_uuid);

-- Messages SMS (Cloud)
CREATE TABLE IF NOT EXISTS phone_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_number TEXT NOT NULL,
    recipient_number TEXT NOT NULL,
    content TEXT NOT NULL,
    sent_at INTEGER NOT NULL,
    is_read INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_messages_sender ON phone_messages(sender_number);
CREATE INDEX IF NOT EXISTS idx_messages_recipient ON phone_messages(recipient_number);
CREATE INDEX IF NOT EXISTS idx_messages_date ON phone_messages(sent_at DESC);

-- Historique d appels (Cloud)
CREATE TABLE IF NOT EXISTS phone_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    caller_number TEXT NOT NULL,
    callee_number TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    duration_seconds INTEGER DEFAULT 0,
    status TEXT DEFAULT 'COMPLETED',
    cost_credits INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_calls_caller ON phone_calls(caller_number);
CREATE INDEX IF NOT EXISTS idx_calls_callee ON phone_calls(callee_number);

-- ========================================
-- SECTION 17: MDT RUSH (Mini-jeu)
-- ========================================

-- Statistiques des joueurs MDT
CREATE TABLE IF NOT EXISTS mdt_player_stats (
    player_uuid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    games_played INTEGER DEFAULT 0,
    games_won INTEGER DEFAULT 0,
    beds_destroyed INTEGER DEFAULT 0,
    kills INTEGER DEFAULT 0,
    deaths INTEGER DEFAULT 0,
    last_played TEXT
);

CREATE INDEX IF NOT EXISTS idx_mdt_stats_wins ON mdt_player_stats(games_won);
CREATE INDEX IF NOT EXISTS idx_mdt_stats_games ON mdt_player_stats(games_played);

-- Sauvegarde des inventaires MDT
CREATE TABLE IF NOT EXISTS mdt_inventory_backup (
    player_uuid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    inventory_data TEXT NOT NULL,
    armor_data TEXT,
    offhand_data TEXT,
    exp_level INTEGER DEFAULT 0,
    exp_progress REAL DEFAULT 0.0,
    health REAL DEFAULT 20.0,
    food_level INTEGER DEFAULT 20,
    gamemode TEXT DEFAULT 'SURVIVAL',
    saved_at TEXT NOT NULL
);

-- Historique des parties MDT
CREATE TABLE IF NOT EXISTS mdt_game_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id TEXT UNIQUE NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT,
    winning_team TEXT,
    duration_seconds INTEGER,
    red_players TEXT,
    blue_players TEXT
);

CREATE INDEX IF NOT EXISTS idx_mdt_history_date ON mdt_game_history(start_time DESC);
