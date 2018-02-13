/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.tuple.Pair;
import org.semux.config.Config;
import org.semux.config.Constants;
import org.semux.core.Genesis.Premine;
import org.semux.core.exception.BlockchainException;
import org.semux.core.state.AccountState;
import org.semux.core.state.AccountStateImpl;
import org.semux.core.state.Delegate;
import org.semux.core.state.DelegateState;
import org.semux.core.state.DelegateStateImpl;
import org.semux.crypto.Hex;
import org.semux.db.Db;
import org.semux.db.DbFactory;
import org.semux.db.DbName;
import org.semux.util.Bytes;
import org.semux.util.SimpleDecoder;
import org.semux.util.SimpleEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blockchain implementation.
 *
 * <pre>
 * index DB structure:
 *
 * [0] => [latest_block_number]
 * [1] => [validators]
 * [2, address] => [validator_stats]
 *
 * [3, block_hash] => [block_number]
 * [4, transaction_hash] => [block_number, from, to] | [coinbase_transaction]
 * [5, address, n] => [transaction_hash]
 * </pre>
 *
 * <pre>
 * block DB structure:
 *
 * [0, block_number] => [block_header]
 * [1, block_number] => [block_transactions]
 * [2, block_number] => [block_results]
 * [3, block_number] => [block_votes]
 * </pre>
 *
 */
public class BlockchainImpl implements Blockchain {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainImpl.class);

    protected static final byte TYPE_LATEST_BLOCK_NUMBER = 0;
    protected static final byte TYPE_VALIDATORS = 1;
    protected static final byte TYPE_VALIDATOR_STATS = 2;
    protected static final byte TYPE_BLOCK_HASH = 3;
    protected static final byte TYPE_TRANSACTION_HASH = 4;
    protected static final byte TYPE_ACCOUNT_TRANSACTION = 5;
    protected static final byte TYPE_RECENT_VALIDATOR_STATS = 6;

    protected static final byte TYPE_BLOCK_HEADER = 0;
    protected static final byte TYPE_BLOCK_TRANSACTIONS = 1;
    protected static final byte TYPE_BLOCK_RESULTS = 2;
    protected static final byte TYPE_BLOCK_VOTES = 3;

    // how long to report validator stats for
    public static final long VALIDATOR_STATS_MAX_HISTORY_BLOCKS = Constants.BLOCKS_PER_DAY * 30;

    protected enum StatsType {
        FORGED, HIT, MISSED
    }

    private Config config;

    private Db indexDB;
    private Db blockDB;

    private AccountState accountState;
    private DelegateState delegateState;

    private Genesis genesis;
    private Block latestBlock;

    private List<BlockchainListener> listeners = new ArrayList<>();

    /**
     * Create a blockchain instance.
     *
     * @param factory
     */
    public BlockchainImpl(Config config, DbFactory factory) {
        this.config = config;

        this.indexDB = factory.getDB(DbName.INDEX);
        this.blockDB = factory.getDB(DbName.BLOCK);

        this.accountState = new AccountStateImpl(factory.getDB(DbName.ACCOUNT));
        this.delegateState = new DelegateStateImpl(this, factory.getDB(DbName.DELEGATE), factory.getDB(DbName.VOTE));

        this.genesis = Genesis.load(config.network());

        byte[] number = indexDB.get(Bytes.of(TYPE_LATEST_BLOCK_NUMBER));
        if (number == null) {
            // pre-allocation
            for (Premine p : genesis.getPremines().values()) {
                accountState.adjustAvailable(p.getAddress(), p.getAmount() * Unit.SEM);
            }
            accountState.commit();

            // delegates
            for (Entry<String, byte[]> e : genesis.getDelegates().entrySet()) {
                delegateState.register(e.getValue(), Bytes.of(e.getKey()), 0);
            }
            delegateState.commit();

            // add block
            addBlock(genesis);
        } else {
            latestBlock = getBlock(Bytes.toLong(number));
        }
    }

    @Override
    public AccountState getAccountState() {
        return accountState;
    }

    @Override
    public DelegateState getDelegateState() {
        return delegateState;
    }

    @Override
    public Block getLatestBlock() {
        return latestBlock;
    }

    @Override
    public long getLatestBlockNumber() {
        return latestBlock.getNumber();
    }

    @Override
    public byte[] getLatestBlockHash() {
        return latestBlock.getHash();
    }

    @Override
    public long getBlockNumber(byte[] hash) {
        byte[] number = indexDB.get(Bytes.merge(TYPE_BLOCK_HASH, hash));
        return (number == null) ? -1 : Bytes.toLong(number);
    }

    @Override
    public Block getBlock(long number) {
        byte[] header = blockDB.get(Bytes.merge(TYPE_BLOCK_HEADER, Bytes.of(number)));
        byte[] transactions = blockDB.get(Bytes.merge(TYPE_BLOCK_TRANSACTIONS, Bytes.of(number)));
        byte[] results = blockDB.get(Bytes.merge(TYPE_BLOCK_RESULTS, Bytes.of(number)));
        byte[] votes = blockDB.get(Bytes.merge(TYPE_BLOCK_VOTES, Bytes.of(number)));

        return (header == null) ? null : Block.fromBytes(header, transactions, results, votes);
    }

    @Override
    public Block getBlock(byte[] hash) {
        long number = getBlockNumber(hash);
        return (number == -1) ? null : getBlock(number);
    }

    @Override
    public BlockHeader getBlockHeader(long number) {
        byte[] header = blockDB.get(Bytes.merge(TYPE_BLOCK_HEADER, Bytes.of(number)));
        return (header == null) ? null : BlockHeader.fromBytes(header);
    }

    @Override
    public BlockHeader getBlockHeader(byte[] hash) {
        long number = getBlockNumber(hash);
        return (number == -1) ? null : getBlockHeader(number);
    }

    @Override
    public boolean hasBlock(long number) {
        return blockDB.get(Bytes.merge(TYPE_BLOCK_HEADER, Bytes.of(number))) != null;
    }

    @Override
    public Transaction getTransaction(byte[] hash) {
        byte[] bytes = indexDB.get(Bytes.merge(TYPE_TRANSACTION_HASH, hash));
        if (bytes != null) {
            // coinbase transaction
            if (bytes.length > 64) {
                return Transaction.fromBytes(bytes);
            }

            SimpleDecoder dec = new SimpleDecoder(bytes);
            long number = dec.readLong();
            int start = dec.readInt();
            dec.readInt();

            byte[] transactions = blockDB.get(Bytes.merge(TYPE_BLOCK_TRANSACTIONS, Bytes.of(number)));
            dec = new SimpleDecoder(transactions, start);
            return Transaction.fromBytes(dec.readBytes());
        }

        return null;
    }

    @Override
    public boolean hasTransaction(final byte[] hash) {
        return indexDB.get(Bytes.merge(TYPE_TRANSACTION_HASH, hash)) != null;
    }

    @Override
    public TransactionResult getTransactionResult(byte[] hash) {
        byte[] bytes = indexDB.get(Bytes.merge(TYPE_TRANSACTION_HASH, hash));
        if (bytes != null) {
            // coinbase transaction
            if (bytes.length > 64) {
                return new TransactionResult(true);
            }

            SimpleDecoder dec = new SimpleDecoder(bytes);
            long number = dec.readLong();
            dec.readInt();
            int start = dec.readInt();

            byte[] results = blockDB.get(Bytes.merge(TYPE_BLOCK_RESULTS, Bytes.of(number)));
            dec = new SimpleDecoder(results, start);
            return TransactionResult.fromBytes(dec.readBytes());
        }

        return null;
    }

    @Override
    public long getTransactionBlockNumber(byte[] hash) {
        Transaction tx = getTransaction(hash);
        if (tx.getType() == TransactionType.COINBASE) {
            return tx.getNonce();
        }

        byte[] bytes = indexDB.get(Bytes.merge(TYPE_TRANSACTION_HASH, hash));
        if (bytes != null) {
            SimpleDecoder dec = new SimpleDecoder(bytes);
            return dec.readLong();
        }

        return -1;
    }

    @Override
    public synchronized void addBlock(Block block) {
        long number = block.getNumber();
        byte[] hash = block.getHash();

        if (number != genesis.getNumber() && number != latestBlock.getNumber() + 1) {
            logger.error("Adding wrong block: number = {}, expected = {}", number, latestBlock.getNumber() + 1);
            throw new BlockchainException("Blocks can only be added sequentially");
        }

        // [1] update block
        blockDB.put(Bytes.merge(TYPE_BLOCK_HEADER, Bytes.of(number)), block.toBytesHeader());
        blockDB.put(Bytes.merge(TYPE_BLOCK_TRANSACTIONS, Bytes.of(number)), block.toBytesTransactions());
        blockDB.put(Bytes.merge(TYPE_BLOCK_RESULTS, Bytes.of(number)), block.toBytesResults());
        blockDB.put(Bytes.merge(TYPE_BLOCK_VOTES, Bytes.of(number)), block.toBytesVotes());

        indexDB.put(Bytes.merge(TYPE_BLOCK_HASH, hash), Bytes.of(number));

        // [2] update transaction indices
        List<Transaction> txs = block.getTransactions();
        List<Pair<Integer, Integer>> txIndices = block.getTransactionIndices();
        long reward = config.getBlockReward(number);

        for (int i = 0; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            reward += tx.getFee();

            SimpleEncoder enc = new SimpleEncoder();
            enc.writeLong(number);
            enc.writeInt(txIndices.get(i).getLeft());
            enc.writeInt(txIndices.get(i).getRight());

            indexDB.put(Bytes.merge(TYPE_TRANSACTION_HASH, tx.getHash()), enc.toBytes());

            // [3] update transaction_by_account index
            addTransactionToAccount(tx, tx.getFrom());
            if (!Arrays.equals(tx.getFrom(), tx.getTo())) {
                addTransactionToAccount(tx, tx.getTo());
            }
        }

        if (number != genesis.getNumber()) {
            // [4] coinbase transaction
            Transaction tx = new Transaction(config.network(),
                    TransactionType.COINBASE,
                    block.getCoinbase(),
                    reward,
                    0,
                    block.getNumber(),
                    block.getTimestamp(),
                    Bytes.EMPTY_BYTES);
            tx.sign(Constants.COINBASE_KEY);
            indexDB.put(Bytes.merge(TYPE_TRANSACTION_HASH, tx.getHash()), tx.toBytes());
            addTransactionToAccount(tx, block.getCoinbase());

            // [5] update validator statistics
            List<String> validators = getValidators();
            String primary = config.getPrimaryValidator(validators, number, 0);
            Long blockNumber = block.getNumber();
            adjustValidatorStats(blockNumber, block.getCoinbase(), StatsType.FORGED, 1);
            if (primary.equals(Hex.encode(block.getCoinbase()))) {
                adjustValidatorStats(blockNumber, Hex.decode0x(primary), StatsType.HIT, 1);
            } else {
                adjustValidatorStats(blockNumber, Hex.decode0x(primary), StatsType.MISSED, 1);
            }
        }

        // [6] update validator set
        if (number % config.getValidatorUpdateInterval() == 0) {
            updateValidators(block.getNumber());
        }

        // [7] update latest_block
        latestBlock = block;
        indexDB.put(Bytes.of(TYPE_LATEST_BLOCK_NUMBER), Bytes.of(number));

        for (BlockchainListener listener : listeners) {
            listener.onBlockAdded(block);
        }
    }

    @Override
    public Genesis getGenesis() {
        return genesis;
    }

    @Override
    public void addListener(BlockchainListener listener) {
        listeners.add(listener);
    }

    @Override
    public int getTransactionCount(byte[] address) {
        byte[] cnt = indexDB.get(Bytes.merge(TYPE_ACCOUNT_TRANSACTION, address));
        return (cnt == null) ? 0 : Bytes.toInt(cnt);
    }

    @Override
    public List<Transaction> getTransactions(byte[] address, int from, int to) {
        List<Transaction> list = new ArrayList<>();

        int total = getTransactionCount(address);
        for (int i = from; i < total && i < to; i++) {
            byte[] key = getNthTransactionIndexKey(address, i);
            byte[] value = indexDB.get(key);
            list.add(getTransaction(value));
        }

        return list;
    }

    @Override
    public List<String> getValidators() {
        List<String> validators = new ArrayList<>();

        byte[] v = indexDB.get(Bytes.of(TYPE_VALIDATORS));
        if (v != null) {
            SimpleDecoder dec = new SimpleDecoder(v);
            int n = dec.readInt();
            for (int i = 0; i < n; i++) {
                validators.add(dec.readString());
            }
        }

        return validators;
    }

    @Override
    public ValidatorStats getValidatorStats(byte[] address) {
        byte[] key = Bytes.merge(TYPE_VALIDATOR_STATS, address);
        byte[] value = indexDB.get(key);

        return (value == null) ? new ValidatorStats(0, 0, 0) : ValidatorStats.fromBytes(value);
    }

    @Override
    public RecentValidatorStats getRecentValidatorStats(long currentBlockNumber, byte[] address) {

        byte[] key = Bytes.merge(TYPE_RECENT_VALIDATOR_STATS, address);
        byte[] value = indexDB.get(key);

        return (value == null)
                ? new RecentValidatorStats(Collections.emptySet(), Collections.emptySet(), Collections.emptySet())
                : RecentValidatorStats.fromBytes(value);
    }

    /**
     * Updates the validator set.
     *
     * @param number
     *            number of validators
     */
    protected void updateValidators(long number) {
        List<String> validators = new ArrayList<>();

        List<Delegate> delegates = delegateState.getDelegates();
        int max = Math.min(delegates.size(), config.getNumberOfValidators(number));
        for (int i = 0; i < max; i++) {
            Delegate d = delegates.get(i);
            validators.add(Hex.encode(d.getAddress()));
        }

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeInt(validators.size());
        for (String v : validators) {
            enc.writeString(v);
        }
        indexDB.put(Bytes.of(TYPE_VALIDATORS), enc.toBytes());
    }

    /**
     * Adjusts validator statistics.
     *
     * @param blockNumber
     *            block number
     * @param address
     *            validator address
     * @param type
     *            stats type
     * @param delta
     *            difference
     */
    protected void adjustValidatorStats(Long blockNumber, byte[] address, StatsType type, long delta) {
        byte[] statsKey = Bytes.merge(TYPE_VALIDATOR_STATS, address);
        byte[] recentStatsKey = Bytes.merge(TYPE_RECENT_VALIDATOR_STATS, address);

        byte[] statsValue = indexDB.get(statsKey);
        byte[] recentStatsValue = indexDB.get(recentStatsKey);

        ValidatorStats stats = (statsValue == null) ? new ValidatorStats(0, 0, 0)
                : ValidatorStats.fromBytes(statsValue);
        RecentValidatorStats recentStats = (recentStatsValue == null)
                ? new RecentValidatorStats(Collections.emptySet(), Collections.emptySet(), Collections.emptySet())
                : RecentValidatorStats.fromBytes(recentStatsValue);

        switch (type) {
        case FORGED:
            stats.setBlocksForged(stats.getBlocksForged() + delta);
            recentStats.addRecentBlockForged(blockNumber);
            break;
        case HIT:
            stats.setTurnsHit(stats.getTurnsHit() + delta);
            recentStats.addRecentTurnsHit(blockNumber);
            break;
        case MISSED:
            stats.setTurnsMissed(stats.getTurnsMissed() + delta);
            recentStats.addRecentTurnsMissed(blockNumber);
            break;
        default:
            break;
        }

        indexDB.put(statsKey, stats.toBytes());
        indexDB.put(recentStatsKey, recentStats.toBytes());
    }

    /**
     * Sets the total number of transaction of an account.
     *
     * @param address
     * @param total
     */
    protected void setTransactionCount(byte[] address, int total) {
        indexDB.put(Bytes.merge(TYPE_ACCOUNT_TRANSACTION, address), Bytes.of(total));
    }

    /**
     * Adds a transaction to an account.
     *
     * @param tx
     * @param address
     */
    protected void addTransactionToAccount(Transaction tx, byte[] address) {
        int total = getTransactionCount(address);
        indexDB.put(getNthTransactionIndexKey(address, total), tx.getHash());
        setTransactionCount(address, total + 1);
    }

    /**
     * Returns the N-th transaction index key of an account.
     *
     * @param address
     * @param n
     * @return
     */
    protected byte[] getNthTransactionIndexKey(byte[] address, int n) {
        return Bytes.merge(Bytes.of(TYPE_ACCOUNT_TRANSACTION), address, Bytes.of(n));
    }

    public static class RecentValidatorStats {

        private Set<Long> recentBlocksForged = new TreeSet<>();
        private Set<Long> recentTurnsHit = new TreeSet<>();
        private Set<Long> recentTurnsMissed = new TreeSet<>();

        public RecentValidatorStats(Set<Long> recentBlocksForged, Set<Long> recentTurnsHit,
                Set<Long> recentTurnsMissed) {
            this.recentBlocksForged.addAll(recentBlocksForged);
            this.recentTurnsHit.addAll(recentTurnsHit);
            this.recentTurnsMissed.addAll(recentTurnsMissed);
        }

        public long getRecentBlocksForged(Long currentBlock, Long timePeriod) {
            return count(currentBlock - timePeriod, recentBlocksForged);
        }

        public long getRecentTurnsHit(Long currentBlock, Long timePeriod) {
            return count(currentBlock - timePeriod, recentTurnsHit);
        }

        public long getRecentTurnsMissed(Long currentBlock, Long timePeriod) {
            return count(currentBlock - timePeriod, recentTurnsMissed);
        }

        public void addRecentBlockForged(Long blockNumber) {
            recentBlocksForged.add(blockNumber);
            prune(blockNumber, recentBlocksForged);
        }

        public void addRecentTurnsHit(Long blockNumber) {
            recentTurnsHit.add(blockNumber);
            prune(blockNumber, recentTurnsHit);
        }

        public void addRecentTurnsMissed(Long blockNumber) {
            recentTurnsMissed.add(blockNumber);
            prune(blockNumber, recentTurnsMissed);
        }

        private long count(Long oldestBlock, Set<Long> blocks) {
            long count = 0;
            for (Long block : blocks) {
                if (block >= oldestBlock) {
                    count++;
                }
            }
            return count;
        }

        private void prune(Long blockNumber, Set<Long> blocks) {
            Iterator<Long> iterator = blocks.iterator();
            while (iterator.hasNext()) {
                Long block = iterator.next();
                if (block >= (blockNumber - VALIDATOR_STATS_MAX_HISTORY_BLOCKS)) {
                    // sorted set, so can break
                    break;
                } else {
                    iterator.remove();
                }
            }
        }

        public byte[] toBytes() {
            SimpleEncoder enc = new SimpleEncoder();
            // if historical value changes in future, we can trigger recalculation
            enc.writeLong(VALIDATOR_STATS_MAX_HISTORY_BLOCKS);
            enc.writeLongSet(recentBlocksForged, true);
            enc.writeLongSet(recentTurnsHit, true);
            enc.writeLongSet(recentTurnsMissed, true);
            return enc.toBytes();
        }

        public static RecentValidatorStats fromBytes(byte[] bytes) {
            SimpleDecoder dec = new SimpleDecoder(bytes);
            Long historicalBlockSize = dec.readLong();
            if (!historicalBlockSize.equals(VALIDATOR_STATS_MAX_HISTORY_BLOCKS)) {
                return new RecentValidatorStats(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
            }
            Set<Long> recentBlocksForged = dec.readLongSet(true);
            Set<Long> recentTurnsHit = dec.readLongSet(true);
            Set<Long> recentTurnsMissed = dec.readLongSet(true);
            return new RecentValidatorStats(recentBlocksForged, recentTurnsHit, recentTurnsMissed);

        }
    }

    /**
     * Validator statistics.
     *
     */
    public static class ValidatorStats {
        private long blocksForged;
        private long turnsHit;
        private long turnsMissed;

        public ValidatorStats(long forged, long hit, long missed) {
            this.blocksForged = forged;
            this.turnsHit = hit;
            this.turnsMissed = missed;
        }

        public long getBlocksForged() {
            return blocksForged;
        }

        void setBlocksForged(long forged) {
            this.blocksForged = forged;
        }

        public long getTurnsHit() {
            return turnsHit;
        }

        void setTurnsHit(long hit) {
            this.turnsHit = hit;
        }

        public long getTurnsMissed() {
            return turnsMissed;
        }

        void setTurnsMissed(long missed) {
            this.turnsMissed = missed;
        }

        public byte[] toBytes() {
            SimpleEncoder enc = new SimpleEncoder();
            enc.writeLong(blocksForged);
            enc.writeLong(turnsHit);
            enc.writeLong(turnsMissed);
            return enc.toBytes();
        }

        public static ValidatorStats fromBytes(byte[] bytes) {
            SimpleDecoder dec = new SimpleDecoder(bytes);
            long forged = dec.readLong();
            long hit = dec.readLong();
            long missed = dec.readLong();
            return new ValidatorStats(forged, hit, missed);
        }
    }
}
