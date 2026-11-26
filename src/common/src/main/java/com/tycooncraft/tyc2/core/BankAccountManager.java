package com.tycooncraft.tyc2.core;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure-Java banking engine managing Personal Player Accounts and Guild/Company
 * Shared Accounts, supporting instant 1:1 deposits, withdrawals, transfers,
 * active settlement account routing, bounded 32-entry transaction logs, and
 * SavedData-ready serialization.
 */
public final class BankAccountManager {

    /** Maximum number of transaction log entries retained per account. */
    public static final int MAX_TRANSACTION_LOGS = 32;

    /** Maximum number of companies a single player may belong to at once. */
    public static final int MAX_COMPANIES_PER_PLAYER = 16;

    /** Maximum display name length for a company/guild account. */
    public static final int MAX_COMPANY_NAME_LENGTH = 32;

    private static final SecureRandom INVITE_RANDOM = new SecureRandom();
    private static final char[] INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /**
     * Scope of a settlement account.
     */
    public enum AccountScope {
        PERSONAL,
        COMPANY
    }

    /**
     * Categorized banking ledger event types.
     */
    public enum TransactionType {
        DEPOSIT,
        WITHDRAW,
        TRANSFER_OUT,
        TRANSFER_IN,
        TRADE_BUY,
        TRADE_SELL,
        NEGOTIATION,
        COMPANY_DISSOLVE,
        SYSTEM_ADJUST
    }

    /**
     * Immutable pointer identifying a personal or company account.
     *
     * @param scope personal or company scope
     * @param accountId player UUID string (personal) or companyId (company)
     */
    public record AccountRef(AccountScope scope, String accountId) {
        /**
         * Creates a personal account reference.
         *
         * @param playerUuid player UUID
         * @return personal account ref
         */
        public static AccountRef personal(UUID playerUuid) {
            return new AccountRef(AccountScope.PERSONAL, Objects.requireNonNull(playerUuid).toString());
        }

        /**
         * Creates a company account reference.
         *
         * @param companyId company identifier
         * @return company account ref
         */
        public static AccountRef company(String companyId) {
            return new AccountRef(AccountScope.COMPANY, Objects.requireNonNull(companyId));
        }
    }

    /**
     * Immutable single transaction record stored in an account's ledger.
     *
     * @param timestamp world tick or epoch millis
     * @param type transaction category
     * @param delta signed IC balance change (+credit, -debit)
     * @param balanceAfter resulting balance after the transaction
     * @param counterparty player/company/village label involved
     * @param memo optional note
     */
    public record BankTransactionLog(
            long timestamp,
            TransactionType type,
            long delta,
            long balanceAfter,
            String counterparty,
            String memo
    ) {
        /**
         * Serializes this log entry into a JSON-compatible map.
         *
         * @return map representation
         */
        public Map<String, Object> toDataMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("timestamp", timestamp);
            map.put("type", type.name());
            map.put("delta", delta);
            map.put("balanceAfter", balanceAfter);
            map.put("counterparty", counterparty == null ? "" : counterparty);
            map.put("memo", memo == null ? "" : memo);
            return map;
        }

        /**
         * Deserializes a log entry from a map.
         *
         * @param map source map
         * @return parsed transaction log
         */
        public static BankTransactionLog fromDataMap(Map<String, Object> map) {
            long ts = JsonLite.getLong(map, "timestamp", 0L);
            String typeName = JsonLite.getString(map, "type", TransactionType.SYSTEM_ADJUST.name());
            TransactionType type;
            try {
                type = TransactionType.valueOf(typeName);
            } catch (IllegalArgumentException ex) {
                type = TransactionType.SYSTEM_ADJUST;
            }
            long delta = JsonLite.getLong(map, "delta", 0L);
            long balAfter = JsonLite.getLong(map, "balanceAfter", 0L);
            String cp = JsonLite.getString(map, "counterparty", "");
            String memo = JsonLite.getString(map, "memo", "");
            return new BankTransactionLog(ts, type, delta, balAfter, cp, memo);
        }
    }

    /**
     * Outcome returned by banking operations.
     *
     * @param success whether the operation succeeded
     * @param errorKey localized error key when {@code success == false}, or {@code null} on success
     * @param accountRef affected account reference (may be {@code null} if lookup failed)
     * @param newBalance resulting balance of the primary affected account
     */
    public record BankResult(boolean success, String errorKey, AccountRef accountRef, long newBalance) {
        static BankResult ok(AccountRef ref, long balance) {
            return new BankResult(true, null, ref, balance);
        }

        static BankResult fail(String errorKey, AccountRef ref, long balance) {
            return new BankResult(false, errorKey, ref, balance);
        }
    }

    /**
     * Mutable personal bank account bound to a Minecraft player UUID.
     */
    public static final class PersonalAccount {
        private final UUID playerUuid;
        private String displayName;
        private long balance;
        private String activeCompanyId;
        private final Deque<BankTransactionLog> logs = new ArrayDeque<>(MAX_TRANSACTION_LOGS);

        PersonalAccount(UUID playerUuid, String displayName, long balance, String activeCompanyId) {
            this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            this.displayName = (displayName == null || displayName.isBlank()) ? playerUuid.toString() : displayName;
            this.balance = Math.max(0L, balance);
            this.activeCompanyId = (activeCompanyId == null || activeCompanyId.isBlank()) ? null : activeCompanyId;
        }

        /** @return owner player UUID */
        public UUID playerUuid() {
            return playerUuid;
        }

        /** @return latest known player display name */
        public String displayName() {
            return displayName;
        }

        /** @return current personal balance in IC */
        public long balance() {
            return balance;
        }

        /** @return active company ID if settling through a guild/company, otherwise {@code null} */
        public String activeCompanyId() {
            return activeCompanyId;
        }

        /** @return unmodifiable chronological list of up to 32 recent transactions */
        public List<BankTransactionLog> transactionLogs() {
            return Collections.unmodifiableList(new ArrayList<>(logs));
        }

        void appendLog(BankTransactionLog entry) {
            while (logs.size() >= MAX_TRANSACTION_LOGS) {
                logs.removeFirst();
            }
            logs.addLast(entry);
        }

        Map<String, Object> toDataMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("playerUuid", playerUuid.toString());
            map.put("displayName", displayName);
            map.put("balance", balance);
            if (activeCompanyId != null) {
                map.put("activeCompanyId", activeCompanyId);
            }
            List<Map<String, Object>> logMaps = new ArrayList<>(logs.size());
            for (BankTransactionLog log : logs) {
                logMaps.add(log.toDataMap());
            }
            map.put("logs", logMaps);
            return map;
        }
    }

    /**
     * Mutable shared Guild/Company bank account accessible by all member players.
     */
    public static final class CompanyAccount {
        private final String companyId;
        private String name;
        private UUID ownerUuid;
        private final Set<UUID> memberUuids = new LinkedHashSet<>();
        private String inviteCode;
        private long balance;
        private final Deque<BankTransactionLog> logs = new ArrayDeque<>(MAX_TRANSACTION_LOGS);

        CompanyAccount(String companyId, String name, UUID ownerUuid, String inviteCode, long balance) {
            this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
            this.name = Objects.requireNonNull(name, "company name must not be null");
            this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid must not be null");
            this.memberUuids.add(ownerUuid);
            this.inviteCode = Objects.requireNonNull(inviteCode, "inviteCode must not be null");
            this.balance = Math.max(0L, balance);
        }

        /** @return unique company identifier */
        public String companyId() {
            return companyId;
        }

        /** @return display name of the guild/company */
        public String name() {
            return name;
        }

        /** @return current company owner UUID */
        public UUID ownerUuid() {
            return ownerUuid;
        }

        /** @return unmodifiable set of member UUIDs (always includes owner) */
        public Set<UUID> memberUuids() {
            return Collections.unmodifiableSet(memberUuids);
        }

        /** @return current invite code for joining this company */
        public String inviteCode() {
            return inviteCode;
        }

        /** @return shared company balance in IC */
        public long balance() {
            return balance;
        }

        /** @return unmodifiable chronological list of up to 32 recent transactions */
        public List<BankTransactionLog> transactionLogs() {
            return Collections.unmodifiableList(new ArrayList<>(logs));
        }

        /**
         * Checks whether a player UUID is a member of this company.
         *
         * @param playerUuid candidate UUID
         * @return {@code true} if member
         */
        public boolean isMember(UUID playerUuid) {
            return playerUuid != null && memberUuids.contains(playerUuid);
        }

        void appendLog(BankTransactionLog entry) {
            while (logs.size() >= MAX_TRANSACTION_LOGS) {
                logs.removeFirst();
            }
            logs.addLast(entry);
        }

        Map<String, Object> toDataMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("companyId", companyId);
            map.put("name", name);
            map.put("ownerUuid", ownerUuid.toString());
            List<String> members = new ArrayList<>(memberUuids.size());
            for (UUID member : memberUuids) {
                members.add(member.toString());
            }
            map.put("memberUuids", members);
            map.put("inviteCode", inviteCode);
            map.put("balance", balance);
            List<Map<String, Object>> logMaps = new ArrayList<>(logs.size());
            for (BankTransactionLog log : logs) {
                logMaps.add(log.toDataMap());
            }
            map.put("logs", logMaps);
            return map;
        }
    }

    private final Map<UUID, PersonalAccount> personalAccounts = new LinkedHashMap<>();
    private final Map<String, CompanyAccount> companyAccounts = new LinkedHashMap<>();
    private long nextCompanySequence = 1L;

    /**
     * Creates an empty bank manager.
     */
    public BankAccountManager() {
    }

    /**
     * Returns an existing personal account or creates one with balance {@code 0}.
     * Also updates the cached display name when non-blank.
     *
     * @param playerUuid player UUID
     * @param displayName current player username
     * @return personal account
     */
    public PersonalAccount getOrCreatePersonal(UUID playerUuid, String displayName) {
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        PersonalAccount existing = personalAccounts.get(playerUuid);
        if (existing != null) {
            if (displayName != null && !displayName.isBlank()) {
                existing.displayName = displayName;
            }
            return existing;
        }
        PersonalAccount created = new PersonalAccount(playerUuid, displayName, 0L, null);
        personalAccounts.put(playerUuid, created);
        return created;
    }

    /**
     * Looks up a personal account by player UUID.
     *
     * @param playerUuid player UUID
     * @return optional personal account
     */
    public Optional<PersonalAccount> getPersonal(UUID playerUuid) {
        return Optional.ofNullable(personalAccounts.get(playerUuid));
    }

    /**
     * Looks up a personal account by case-insensitive display name.
     *
     * @param displayName player name
     * @return optional matching account
     */
    public Optional<PersonalAccount> findPersonalByName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return Optional.empty();
        }
        String needle = displayName.trim();
        for (PersonalAccount account : personalAccounts.values()) {
            if (account.displayName.equalsIgnoreCase(needle)) {
                return Optional.of(account);
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up a company account by ID.
     *
     * @param companyId company identifier
     * @return optional company account
     */
    public Optional<CompanyAccount> getCompany(String companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(companyAccounts.get(companyId));
    }

    /**
     * Returns all companies that the given player belongs to.
     *
     * @param playerUuid player UUID
     * @return list of companies
     */
    public List<CompanyAccount> getCompaniesForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return Collections.emptyList();
        }
        List<CompanyAccount> result = new ArrayList<>();
        for (CompanyAccount company : companyAccounts.values()) {
            if (company.isMember(playerUuid)) {
                result.add(company);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Checks whether a player can create or join another company without exceeding
     * {@link #MAX_COMPANIES_PER_PLAYER}.
     *
     * @param playerUuid player UUID
     * @return {@code true} if below the per-player company limit
     */
    public boolean canJoinAnotherCompany(UUID playerUuid) {
        return getCompaniesForPlayer(playerUuid).size() < MAX_COMPANIES_PER_PLAYER;
    }

    /**
     * Creates a new shared Guild/Company account owned by {@code ownerUuid}.
     *
     * @param ownerUuid founding player UUID
     * @param ownerName founding player display name
     * @param companyName desired company name (trimmed and capped at {@value #MAX_COMPANY_NAME_LENGTH} chars)
     * @return created company account
     * @throws IllegalArgumentException if {@code companyName} is blank
     * @throws IllegalStateException if the player already belongs to {@value #MAX_COMPANIES_PER_PLAYER} companies
     */
    public CompanyAccount createCompany(UUID ownerUuid, String ownerName, String companyName) {
        Objects.requireNonNull(ownerUuid, "ownerUuid must not be null");
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name must not be blank");
        }
        if (!canJoinAnotherCompany(ownerUuid)) {
            throw new IllegalStateException("status.tyc2.atm.company_limit");
        }
        String trimmedName = companyName.trim();
        if (trimmedName.length() > MAX_COMPANY_NAME_LENGTH) {
            trimmedName = trimmedName.substring(0, MAX_COMPANY_NAME_LENGTH);
        }
        getOrCreatePersonal(ownerUuid, ownerName);
        String id = "co_" + nextCompanySequence++;
        while (companyAccounts.containsKey(id)) {
            id = "co_" + nextCompanySequence++;
        }
        String code = generateUniqueInviteCode();
        CompanyAccount company = new CompanyAccount(id, trimmedName, ownerUuid, code, 0L);
        companyAccounts.put(id, company);
        return company;
    }

    /**
     * Adds a member to a company account (owner-only operation).
     *
     * @param actorUuid requesting player UUID (must be owner)
     * @param companyId target company ID
     * @param targetUuid player UUID to add
     * @param targetName display name of added player
     * @return operation result
     */
    public BankResult addCompanyMember(UUID actorUuid, String companyId, UUID targetUuid, String targetName) {
        CompanyAccount company = companyAccounts.get(companyId);
        if (company == null) {
            return BankResult.fail("status.tyc2.atm.invalid_recipient", null, 0L);
        }
        if (!company.ownerUuid.equals(actorUuid)) {
            return BankResult.fail("status.tyc2.hub.not_owner", AccountRef.company(companyId), company.balance);
        }
        if (!company.isMember(targetUuid) && !canJoinAnotherCompany(targetUuid)) {
            return BankResult.fail("status.tyc2.atm.company_limit", AccountRef.company(companyId), company.balance);
        }
        getOrCreatePersonal(targetUuid, targetName);
        company.memberUuids.add(targetUuid);
        return BankResult.ok(AccountRef.company(companyId), company.balance);
    }

    /**
     * Joins a company account using its invite code (case-insensitive).
     *
     * @param playerUuid joining player UUID
     * @param displayName joining player name
     * @param inviteCode invite code
     * @return operation result referencing the joined company on success
     */
    public BankResult joinCompanyByInviteCode(UUID playerUuid, String displayName, String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return BankResult.fail("status.tyc2.atm.invalid_invite", null, 0L);
        }
        String normalized = inviteCode.trim().toUpperCase(Locale.ROOT);
        for (CompanyAccount company : companyAccounts.values()) {
            if (company.inviteCode.equalsIgnoreCase(normalized)) {
                if (!company.isMember(playerUuid) && !canJoinAnotherCompany(playerUuid)) {
                    return BankResult.fail("status.tyc2.atm.company_limit", AccountRef.company(company.companyId), company.balance);
                }
                getOrCreatePersonal(playerUuid, displayName);
                company.memberUuids.add(playerUuid);
                return BankResult.ok(AccountRef.company(company.companyId), company.balance);
            }
        }
        return BankResult.fail("status.tyc2.atm.invalid_invite", null, 0L);
    }

    /**
     * Regenerates a company's invite code (owner-only).
     *
     * @param actorUuid requesting player UUID
     * @param companyId company ID
     * @return new invite code, or empty if unauthorized or company missing
     */
    public Optional<String> regenerateInviteCode(UUID actorUuid, String companyId) {
        CompanyAccount company = companyAccounts.get(companyId);
        if (company == null || !company.ownerUuid.equals(actorUuid)) {
            return Optional.empty();
        }
        company.inviteCode = generateUniqueInviteCode();
        return Optional.of(company.inviteCode);
    }

    /**
     * Removes a player from a company account.
     *
     * <p>If the owner leaves while other members remain, ownership transfers to the next member.
     * If the last member leaves, any remaining balance is refunded to their personal account and
     * the company is dissolved so no funds are ever lost.
     *
     * @param playerUuid leaving player UUID
     * @param companyId company ID
     * @param timestamp world tick or epoch millis
     * @return operation result
     */
    public BankResult leaveCompany(UUID playerUuid, String companyId, long timestamp) {
        CompanyAccount company = companyAccounts.get(companyId);
        if (company == null) {
            return BankResult.fail("status.tyc2.atm.invalid_recipient", null, 0L);
        }
        if (!company.isMember(playerUuid)) {
            return BankResult.fail("status.tyc2.hub.not_owner", AccountRef.company(companyId), company.balance);
        }
        company.memberUuids.remove(playerUuid);
        PersonalAccount personal = getOrCreatePersonal(playerUuid, null);
        if (companyId.equals(personal.activeCompanyId)) {
            personal.activeCompanyId = null;
        }

        if (company.memberUuids.isEmpty()) {
            long remaining = company.balance;
            company.balance = 0L;
            if (remaining > 0L) {
                personal.balance = saturatedAdd(personal.balance, remaining);
                personal.appendLog(new BankTransactionLog(
                        timestamp,
                        TransactionType.COMPANY_DISSOLVE,
                        remaining,
                        personal.balance,
                        company.name,
                        "Company dissolved refund"
                ));
            }
            companyAccounts.remove(companyId);
            return BankResult.ok(AccountRef.personal(playerUuid), personal.balance);
        }

        if (company.ownerUuid.equals(playerUuid)) {
            company.ownerUuid = company.memberUuids.iterator().next();
        }
        return BankResult.ok(AccountRef.personal(playerUuid), personal.balance);
    }

    /**
     * Switches a player's active settlement account to a company (if non-null and player is a member)
     * or back to their personal account (if {@code companyId == null}).
     *
     * @param playerUuid player UUID
     * @param companyId target company ID, or {@code null} for personal settlement
     * @return operation result
     */
    public BankResult setActiveCompany(UUID playerUuid, String companyId) {
        PersonalAccount personal = getOrCreatePersonal(playerUuid, null);
        if (companyId == null || companyId.isBlank()) {
            personal.activeCompanyId = null;
            return BankResult.ok(AccountRef.personal(playerUuid), personal.balance);
        }
        CompanyAccount company = companyAccounts.get(companyId);
        if (company == null) {
            return BankResult.fail("status.tyc2.atm.invalid_recipient", AccountRef.personal(playerUuid), personal.balance);
        }
        if (!company.isMember(playerUuid)) {
            return BankResult.fail("status.tyc2.hub.not_owner", AccountRef.personal(playerUuid), personal.balance);
        }
        personal.activeCompanyId = companyId;
        return BankResult.ok(AccountRef.company(companyId), company.balance);
    }

    /**
     * Resolves the effective settlement account (personal vs company) for a player.
     * Falls back cleanly to personal if the active company was dissolved or membership ended.
     *
     * @param playerUuid player UUID
     * @return active settlement account reference
     */
    public AccountRef resolveSettlementAccount(UUID playerUuid) {
        PersonalAccount personal = getOrCreatePersonal(playerUuid, null);
        if (personal.activeCompanyId != null) {
            CompanyAccount company = companyAccounts.get(personal.activeCompanyId);
            if (company != null && company.isMember(playerUuid)) {
                return AccountRef.company(company.companyId);
            }
            personal.activeCompanyId = null;
        }
        return AccountRef.personal(playerUuid);
    }

    /**
     * Returns the current IC balance of a player's active settlement account.
     *
     * @param playerUuid player UUID
     * @return balance in IC
     */
    public long getActiveBalance(UUID playerUuid) {
        return getBalance(resolveSettlementAccount(playerUuid));
    }

    /**
     * Returns the current IC balance for a specific account reference.
     *
     * @param ref account reference
     * @return balance in IC, or {@code 0} if not found
     */
    public long getBalance(AccountRef ref) {
        if (ref == null) {
            return 0L;
        }
        if (ref.scope() == AccountScope.PERSONAL) {
            PersonalAccount acc = personalAccounts.get(UUID.fromString(ref.accountId()));
            return acc == null ? 0L : acc.balance;
        }
        CompanyAccount company = companyAccounts.get(ref.accountId());
        return company == null ? 0L : company.balance;
    }

    /**
     * Returns recent transaction logs for an account reference.
     *
     * @param ref account reference
     * @return unmodifiable list of up to 32 recent logs
     */
    public List<BankTransactionLog> getTransactionLogs(AccountRef ref) {
        if (ref == null) {
            return Collections.emptyList();
        }
        if (ref.scope() == AccountScope.PERSONAL) {
            PersonalAccount acc = personalAccounts.get(UUID.fromString(ref.accountId()));
            return acc == null ? Collections.emptyList() : acc.transactionLogs();
        }
        CompanyAccount company = companyAccounts.get(ref.accountId());
        return company == null ? Collections.emptyList() : company.transactionLogs();
    }

    /**
     * Deposits a positive IC cash amount 1:1 into the player's active settlement account.
     *
     * @param playerUuid player UUID
     * @param amount positive IC amount
     * @param timestamp world tick or epoch millis
     * @param memo optional memo
     * @return bank result
     */
    public BankResult deposit(UUID playerUuid, long amount, long timestamp, String memo) {
        AccountRef ref = resolveSettlementAccount(playerUuid);
        return creditAccount(ref, amount, TransactionType.DEPOSIT, timestamp, "ATM Cash", memo);
    }

    /**
     * Deposits a map of physical coins into the player's active settlement account.
     *
     * @param playerUuid player UUID
     * @param coins coin counts by denomination
     * @param timestamp world tick or epoch millis
     * @param memo optional memo
     * @return bank result
     */
    public BankResult depositCoins(
            UUID playerUuid,
            Map<CurrencyDenomination, Integer> coins,
            long timestamp,
            String memo
    ) {
        long value = CurrencyDenomination.totalValue(coins);
        return deposit(playerUuid, value, timestamp, memo);
    }

    /**
     * Withdraws a positive IC cash amount 1:1 from the player's active settlement account.
     *
     * @param playerUuid player UUID
     * @param amount positive IC amount
     * @param timestamp world tick or epoch millis
     * @param memo optional memo
     * @return bank result
     */
    public BankResult withdraw(UUID playerUuid, long amount, long timestamp, String memo) {
        AccountRef ref = resolveSettlementAccount(playerUuid);
        return debitAccount(ref, amount, TransactionType.WITHDRAW, timestamp, "ATM Cash", memo);
    }

    /**
     * Withdraws a specific count of a chosen denomination from the player's active settlement account.
     *
     * @param playerUuid player UUID
     * @param denomination requested coin denomination
     * @param count requested coin count (>= 1)
     * @param timestamp world tick or epoch millis
     * @param memo optional memo
     * @return bank result
     */
    public BankResult withdrawDenominated(
            UUID playerUuid,
            CurrencyDenomination denomination,
            int count,
            long timestamp,
            String memo
    ) {
        Objects.requireNonNull(denomination, "denomination must not be null");
        if (count <= 0) {
            AccountRef ref = resolveSettlementAccount(playerUuid);
            return BankResult.fail("status.tyc2.atm.invalid_amount", ref, getBalance(ref));
        }
        long amount = Math.multiplyExact(denomination.value(), count);
        return withdraw(playerUuid, amount, timestamp, memo);
    }

    /**
     * Transfers IC 1:1 from the sender's active settlement account to a target player's personal account.
     *
     * @param senderUuid sending player UUID
     * @param targetPlayerUuid receiving player UUID
     * @param amount positive IC amount
     * @param timestamp world tick or epoch millis
     * @param memo optional memo
     * @return bank result for sender's account
     */
    public BankResult transferToPlayer(
            UUID senderUuid,
            UUID targetPlayerUuid,
            long amount,
            long timestamp,
            String memo
    ) {
        getOrCreatePersonal(targetPlayerUuid, null);
        return transfer(resolveSettlementAccount(senderUuid), AccountRef.personal(targetPlayerUuid), amount, timestamp, memo);
    }

    /**
     * Transfers IC 1:1 from the sender's active settlement account to a target company account.
     *
     * @param senderUuid sending player UUID
     * @param targetCompanyId receiving company ID
     * @param amount positive IC amount
     * @param timestamp world tick or epoch millis
     * @param memo optional memo
     * @return bank result for sender's account
     */
    public BankResult transferToCompany(
            UUID senderUuid,
            String targetCompanyId,
            long amount,
            long timestamp,
            String memo
    ) {
        if (!companyAccounts.containsKey(targetCompanyId)) {
            AccountRef from = resolveSettlementAccount(senderUuid);
            return BankResult.fail("status.tyc2.atm.invalid_recipient", from, getBalance(from));
        }
        return transfer(resolveSettlementAccount(senderUuid), AccountRef.company(targetCompanyId), amount, timestamp, memo);
    }

    /**
     * Executes an atomic 1:1 transfer between any two valid {@link AccountRef}s.
     *
     * @param from source account
     * @param to destination account
     * @param amount positive IC amount
     * @param timestamp world tick or epoch millis
     * @param memo optional transfer note
     * @return bank result for source account
     */
    public BankResult transfer(AccountRef from, AccountRef to, long amount, long timestamp, String memo) {
        if (from == null || to == null) {
            return BankResult.fail("status.tyc2.atm.invalid_recipient", from, 0L);
        }
        if (from.equals(to)) {
            return BankResult.fail("status.tyc2.atm.invalid_recipient", from, getBalance(from));
        }
        if (amount <= 0L) {
            return BankResult.fail("status.tyc2.atm.invalid_amount", from, getBalance(from));
        }
        if (!accountExists(from) || !accountExists(to)) {
            return BankResult.fail("status.tyc2.atm.invalid_recipient", from, getBalance(from));
        }
        long sourceBal = getBalance(from);
        if (sourceBal < amount) {
            return BankResult.fail("status.tyc2.atm.insufficient_balance", from, sourceBal);
        }
        if (Long.MAX_VALUE - getBalance(to) < amount) {
            return BankResult.fail("status.tyc2.atm.invalid_amount", from, sourceBal);
        }

        String fromLabel = describeAccount(from);
        String toLabel = describeAccount(to);
        BankResult debitRes = debitAccount(from, amount, TransactionType.TRANSFER_OUT, timestamp, toLabel, memo);
        if (!debitRes.success()) {
            return debitRes;
        }
        creditAccount(to, amount, TransactionType.TRANSFER_IN, timestamp, fromLabel, memo);
        return debitRes;
    }

    /**
     * Credits a specific personal or company account directly (used by trade settlements and deposits).
     *
     * @param ref account reference
     * @param amount positive IC amount
     * @param type transaction log type
     * @param timestamp world tick or epoch millis
     * @param counterparty label of counterparty
     * @param memo optional note
     * @return bank result
     */
    public BankResult creditAccount(
            AccountRef ref,
            long amount,
            TransactionType type,
            long timestamp,
            String counterparty,
            String memo
    ) {
        if (ref == null || !accountExists(ref)) {
            return BankResult.fail("status.tyc2.atm.invalid_recipient", ref, 0L);
        }
        if (amount <= 0L) {
            return BankResult.fail("status.tyc2.atm.invalid_amount", ref, getBalance(ref));
        }
        if (ref.scope() == AccountScope.PERSONAL) {
            PersonalAccount acc = personalAccounts.get(UUID.fromString(ref.accountId()));
            acc.balance = saturatedAdd(acc.balance, amount);
            acc.appendLog(new BankTransactionLog(timestamp, type, amount, acc.balance, counterparty, memo));
            return BankResult.ok(ref, acc.balance);
        }
        CompanyAccount company = companyAccounts.get(ref.accountId());
        company.balance = saturatedAdd(company.balance, amount);
        company.appendLog(new BankTransactionLog(timestamp, type, amount, company.balance, counterparty, memo));
        return BankResult.ok(ref, company.balance);
    }

    /**
     * Debits a specific personal or company account if sufficient balance is available.
     *
     * @param ref account reference
     * @param amount positive IC amount
     * @param type transaction log type
     * @param timestamp world tick or epoch millis
     * @param counterparty label of counterparty
     * @param memo optional note
     * @return bank result
     */
    public BankResult debitAccount(
            AccountRef ref,
            long amount,
            TransactionType type,
            long timestamp,
            String counterparty,
            String memo
    ) {
        if (ref == null || !accountExists(ref)) {
            return BankResult.fail("status.tyc2.atm.invalid_recipient", ref, 0L);
        }
        if (amount <= 0L) {
            return BankResult.fail("status.tyc2.atm.invalid_amount", ref, getBalance(ref));
        }
        if (ref.scope() == AccountScope.PERSONAL) {
            PersonalAccount acc = personalAccounts.get(UUID.fromString(ref.accountId()));
            if (acc.balance < amount) {
                return BankResult.fail("status.tyc2.atm.insufficient_balance", ref, acc.balance);
            }
            acc.balance -= amount;
            acc.appendLog(new BankTransactionLog(timestamp, type, -amount, acc.balance, counterparty, memo));
            return BankResult.ok(ref, acc.balance);
        }
        CompanyAccount company = companyAccounts.get(ref.accountId());
        if (company.balance < amount) {
            return BankResult.fail("status.tyc2.atm.insufficient_balance", ref, company.balance);
        }
        company.balance -= amount;
        company.appendLog(new BankTransactionLog(timestamp, type, -amount, company.balance, counterparty, memo));
        return BankResult.ok(ref, company.balance);
    }

    /**
     * Sums all personal and company bank balances for {@link MacroEconomyEngine} money supply tracking.
     *
     * @return combined IC across all personal and guild accounts
     */
    public long getTotalCombinedBalance() {
        long sum = 0L;
        for (PersonalAccount acc : personalAccounts.values()) {
            sum = saturatedAdd(sum, acc.balance);
        }
        for (CompanyAccount co : companyAccounts.values()) {
            sum = saturatedAdd(sum, co.balance);
        }
        return sum;
    }

    /** @return unmodifiable collection of all personal accounts */
    public Map<UUID, PersonalAccount> allPersonalAccounts() {
        return Collections.unmodifiableMap(personalAccounts);
    }

    /** @return unmodifiable map of all company accounts */
    public Map<String, CompanyAccount> allCompanyAccounts() {
        return Collections.unmodifiableMap(companyAccounts);
    }

    /**
     * Exports full banking state to a pure-Java structured map for NBT or JSON persistence.
     *
     * @return structured data map
     */
    public Map<String, Object> toDataMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("nextCompanySequence", nextCompanySequence);
        List<Map<String, Object>> personals = new ArrayList<>(personalAccounts.size());
        for (PersonalAccount acc : personalAccounts.values()) {
            personals.add(acc.toDataMap());
        }
        root.put("personalAccounts", personals);

        List<Map<String, Object>> companies = new ArrayList<>(companyAccounts.size());
        for (CompanyAccount co : companyAccounts.values()) {
            companies.add(co.toDataMap());
        }
        root.put("companyAccounts", companies);
        return root;
    }

    /**
     * Serializes full banking state into JSON text.
     *
     * @return JSON string
     */
    public String toJson() {
        return JsonLite.toJson(toDataMap());
    }

    /**
     * Restores a {@link BankAccountManager} from a structured data map.
     *
     * @param root persisted data map
     * @return restored manager
     */
    public static BankAccountManager fromDataMap(Map<String, Object> root) {
        BankAccountManager manager = new BankAccountManager();
        if (root == null) {
            return manager;
        }
        manager.nextCompanySequence = Math.max(1L, JsonLite.getLong(root, "nextCompanySequence", 1L));

        List<Object> companies = JsonLite.asArray(root.get("companyAccounts"));
        if (companies != null) {
            for (Object item : companies) {
                Map<String, Object> map = JsonLite.asObject(item);
                if (map == null) {
                    continue;
                }
                String coId = JsonLite.requireString(map, "companyId");
                String name = JsonLite.requireString(map, "name");
                UUID owner = UUID.fromString(JsonLite.requireString(map, "ownerUuid"));
                String code = JsonLite.getString(map, "inviteCode", generateInviteCode(coId, owner));
                long balance = JsonLite.getLong(map, "balance", 0L);
                CompanyAccount co = new CompanyAccount(coId, name, owner, code, balance);
                for (String memberStr : JsonLite.getStringList(map, "memberUuids")) {
                    co.memberUuids.add(UUID.fromString(memberStr));
                }
                List<Object> logs = JsonLite.asArray(map.get("logs"));
                if (logs != null) {
                    for (Object logObj : logs) {
                        Map<String, Object> logMap = JsonLite.asObject(logObj);
                        if (logMap != null) {
                            co.appendLog(BankTransactionLog.fromDataMap(logMap));
                        }
                    }
                }
                manager.companyAccounts.put(coId, co);
            }
        }

        List<Object> personals = JsonLite.asArray(root.get("personalAccounts"));
        if (personals != null) {
            for (Object item : personals) {
                Map<String, Object> map = JsonLite.asObject(item);
                if (map == null) {
                    continue;
                }
                UUID uuid = UUID.fromString(JsonLite.requireString(map, "playerUuid"));
                String name = JsonLite.getString(map, "displayName", uuid.toString());
                long balance = JsonLite.getLong(map, "balance", 0L);
                String activeCo = JsonLite.getString(map, "activeCompanyId", null);
                PersonalAccount acc = new PersonalAccount(uuid, name, balance, activeCo);
                List<Object> logs = JsonLite.asArray(map.get("logs"));
                if (logs != null) {
                    for (Object logObj : logs) {
                        Map<String, Object> logMap = JsonLite.asObject(logObj);
                        if (logMap != null) {
                            acc.appendLog(BankTransactionLog.fromDataMap(logMap));
                        }
                    }
                }
                manager.personalAccounts.put(uuid, acc);
            }
        }
        return manager;
    }

    /**
     * Restores a {@link BankAccountManager} from JSON text.
     *
     * @param json JSON object string
     * @return restored manager
     */
    public static BankAccountManager fromJson(String json) {
        return fromDataMap(JsonLite.parseObject(json));
    }

    private boolean accountExists(AccountRef ref) {
        if (ref.scope() == AccountScope.PERSONAL) {
            try {
                return personalAccounts.containsKey(UUID.fromString(ref.accountId()));
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        return companyAccounts.containsKey(ref.accountId());
    }

    private String describeAccount(AccountRef ref) {
        if (ref.scope() == AccountScope.PERSONAL) {
            PersonalAccount acc = personalAccounts.get(UUID.fromString(ref.accountId()));
            return acc == null ? ref.accountId() : acc.displayName;
        }
        CompanyAccount co = companyAccounts.get(ref.accountId());
        return co == null ? ref.accountId() : co.name;
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 64; attempt++) {
            String candidate = generateRandomInviteCode();
            boolean collision = false;
            for (CompanyAccount existing : companyAccounts.values()) {
                if (existing.inviteCode.equalsIgnoreCase(candidate)) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                return candidate;
            }
        }
        return generateRandomInviteCode();
    }

    private static String generateInviteCode(String companyId, UUID seedUuid) {
        return generateRandomInviteCode();
    }

    private static String generateRandomInviteCode() {
        StringBuilder sb = new StringBuilder(10);
        sb.append("TYC-");
        for (int i = 0; i < 6; i++) {
            sb.append(INVITE_ALPHABET[INVITE_RANDOM.nextInt(INVITE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static long saturatedAdd(long a, long b) {
        if (b <= 0L) {
            return Math.max(0L, a + b);
        }
        if (a <= 0L) {
            return b;
        }
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }
}
