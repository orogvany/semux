/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.core;

import static org.semux.core.Amount.neg;
import static org.semux.core.Amount.sub;
import static org.semux.core.Amount.sum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.semux.config.Config;
import org.semux.core.TransactionResult.Error;
import org.semux.core.state.Account;
import org.semux.core.state.AccountState;
import org.semux.core.state.DelegateState;
import org.semux.util.Bytes;

/**
 * Transaction executor
 */
public class TransactionExecutor {

    private static final boolean[] valid = new boolean[256];
    static {
        for (byte b : Bytes.of("abcdefghijklmnopqrstuvwxyz0123456789_")) {
            valid[b & 0xff] = true;
        }
    }

    /**
     * Validate delegate name.
     * 
     * @param data
     */
    public static boolean validateDelegateName(byte[] data) {
        if (data.length < 3 || data.length > 16) {
            return false;
        }

        for (byte b : data) {
            if (!valid[b & 0xff]) {
                return false;
            }
        }

        return true;
    }

    private Config config;

    /**
     * Creates a new transaction executor.
     * 
     * @param config
     */
    public TransactionExecutor(Config config) {
        this.config = config;
    }

    /**
     * Execute a list of transactions.
     * 
     * NOTE: transaction format and signature are assumed to be success.
     *
     * @param txs
     *            transactions
     * @param as
     *            account state
     * @param ds
     *            delegate state
     * @return
     */
    public List<TransactionResult> execute(List<Transaction> txs, AccountState as, DelegateState ds) {
        List<TransactionResult> results = new ArrayList<>();

        for (Transaction tx : txs) {
            TransactionResult result = new TransactionResult(false);
            results.add(result);

            TransactionType type = tx.getType();
            byte[] from = tx.getFrom();
            byte[] to = tx.getTo();
            Amount value = tx.getValue();
            long nonce = tx.getNonce();
            Amount fee = tx.getFee();
            byte[] data = tx.getData();

            Account acc = as.getAccount(from);
            Amount available = acc.getAvailable();
            Amount locked = acc.getLocked();

            // check nonce
            if (nonce != acc.getNonce()) {
                result.setError(Error.INVALID_NONCE);
                continue;
            }

            // check fee
            if (fee.lt(config.minTransactionFee())) {
                result.setError(Error.INVALID_FEE);
                continue;
            }

            // check data length
            if (data.length > config.maxTransactionDataSize(type)) {
                result.setError(Error.INVALID_DATA_LENGTH);
                continue;
            }

            switch (type) {
            case TRANSFER: {
                if (fee.lte(available) && value.lte(available) && sum(value, fee).lte(available)) {

                    as.adjustAvailable(from, neg(sum(value, fee)));
                    as.adjustAvailable(to, value);

                    result.setSuccess(true);
                } else {
                    result.setError(Error.INSUFFICIENT_AVAILABLE);
                }
                break;
            }
            case DELEGATE: {
                if (!validateDelegateName(data)) {
                    result.setError(Error.INVALID_DELEGATE_NAME);
                    break;
                }
                if (value.lt(config.minDelegateBurnAmount())) {
                    result.setError(Error.INVALID_DELEGATE_BURN_AMOUNT);
                    break;
                }

                if (fee.lte(available) && value.lte(available) && sum(value, fee).lte(available)) {
                    if (Arrays.equals(Bytes.EMPTY_ADDRESS, to) && ds.register(from, data)) {

                        as.adjustAvailable(from, neg(sum(value, fee)));

                        result.setSuccess(true);
                    } else {
                        result.setError(Error.FAILED);
                    }
                } else {
                    result.setError(Error.INSUFFICIENT_AVAILABLE);
                }
                break;
            }
            case VOTE: {
                if (fee.lte(available) && value.lte(available) && sum(value, fee).lte(available)) {
                    if (ds.vote(from, to, value)) {

                        as.adjustAvailable(from, neg(sum(value, fee)));
                        as.adjustLocked(from, value);

                        result.setSuccess(true);
                    } else {
                        result.setError(Error.FAILED);
                    }
                } else {
                    result.setError(Error.INSUFFICIENT_AVAILABLE);
                }
                break;
            }
            case UNVOTE: {
                if (available.lt(fee)) {
                    result.setError(Error.INSUFFICIENT_AVAILABLE);
                    break;
                }

                if (locked.lt(value)) {
                    result.setError(Error.INSUFFICIENT_LOCKED);
                    break;
                }

                if (ds.unvote(from, to, value)) {
                    as.adjustAvailable(from, sub(value, fee));
                    as.adjustLocked(from, neg(value));

                    result.setSuccess(true);
                } else {
                    result.setError(Error.FAILED);
                }
                break;
            }
            default:
                // unsupported transaction type
                result.setError(Error.INVALID_TYPE);
                break;
            }

            // increase nonce if success
            if (result.isSuccess()) {
                as.increaseNonce(from);
            }
        }

        return results;
    }

    /**
     * Execute one transaction.
     * 
     * NOTE: transaction format and signature are assumed to be success.
     * 
     * @param as
     *            account state
     * @param ds
     *            delegate state
     * @return
     */
    public TransactionResult execute(Transaction tx, AccountState as, DelegateState ds) {
        return execute(Collections.singletonList(tx), as, ds).get(0);
    }
}
