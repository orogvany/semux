/**
 * Copyright (c) 2017 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.api;

import java.util.Map;

import io.netty.handler.codec.http.HttpHeaders;
import org.semux.Kernel;
import org.semux.api.response.GetRootResponse;
import org.semux.util.exception.UnreachableException;

/**
 * Semux RESTful API handler implementation.
 *
 * TODO: Auto-generate API docs
 */
public class ApiHandlerImpl implements ApiHandler {

    private final Kernel kernel;
    private final SemuxAPI semuxApi;

    /**
     * Creates an API handler.
     *
     * @param kernel
     */
    public ApiHandlerImpl(Kernel kernel) {
        this.kernel = kernel;
        this.semuxApi = new SemuxApiImpl(kernel);
    }

    @Override
    public ApiHandlerResponse service(String uri, Map<String, String> params, HttpHeaders headers) {
        if ("/".equals(uri)) {
            return new GetRootResponse(true, "Semux API works");
        }

        Command cmd = Command.of(uri.substring(1));
        if (cmd == null) {
            return semuxApi.failure("Invalid request: uri = " + uri);
        }

        try {
            switch (cmd) {
            case GET_INFO:
                return getInfo();

            case GET_PEERS:
                return getPeers();

            case ADD_NODE:
                return addNode(params);

            case ADD_TO_BLACKLIST:
                return addToBlackList(params);

            case ADD_TO_WHITELIST:
                return addToWhiteList(params);

            case GET_LATEST_BLOCK_NUMBER:
                return getLatestBlockNumber();

            case GET_LATEST_BLOCK:
                return getLatestBlock();

            case GET_BLOCK:
                return getBlock(params);

            case GET_PENDING_TRANSACTIONS:
                return getPendingTransactions();

            case GET_ACCOUNT_TRANSACTIONS:
                return getAccountTransactions(params);

            case GET_TRANSACTION:
                return getTransaction(params);

            case SEND_TRANSACTION:
                return sendTransaction(params);

            case GET_ACCOUNT:
                return getAccount(params);

            case GET_DELEGATE:
                return getDelegate(params);

            case GET_VALIDATORS:
                return getValidators();

            case GET_DELEGATES:
                return getDelegates();

            case GET_VOTE:
                return getVote(params);

            case GET_VOTES:
                return getVotes(params);

            case LIST_ACCOUNTS:
                return listAccounts();

            case CREATE_ACCOUNT:
                return createAccount();

            case GET_TRANSACTION_LIMITS:
                return getTransactionLimits(params);

            case TRANSFER:
            case DELEGATE:
            case VOTE:
            case UNVOTE:
                return doTransaction(cmd, params);
            default:
                // should never reach here, otherwise it's the programmer's fault
                throw new UnreachableException();
            }
        } catch (Exception e) {
            return semuxApi.failure("Failed to process your request: " + e.getMessage());
        }
    }

    /**
     * GET /get_info
     *
     * @return
     */
    private ApiHandlerResponse getInfo() {
        return semuxApi.getInfo();
    }

    /**
     * GET /get_peers
     *
     * @return
     */
    private ApiHandlerResponse getPeers() {
       return semuxApi.getPeers();
    }

    /**
     * GET /add_node?node
     *
     * @param params
     * @return result
     */
    private ApiHandlerResponse addNode(Map<String, String> params) {
       return semuxApi.addNode(params.get("node"));
    }

    /**
     * GET /get_block?number|hash
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse getBlock(Map<String, String> params) {
        String number = params.get("number");
        String hash = params.get("hash");

        if (number != null) {
            return semuxApi.getBlock(Long.parseLong(number));
        } else if (hash != null) {
            return semuxApi.getBlock(hash);
        } else {
            return semuxApi.failure("Either parameter `number` or `hash` has to be provided");
        }
    }

    /**
     * GET /get_pending_transactions
     *
     * @return
     */
    private ApiHandlerResponse getPendingTransactions() {
        return semuxApi.getPendingTransactions();
    }

    /**
     * GET /get_account_transactions?address&from&to
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse getAccountTransactions(Map<String, String> params) {
        String address = params.get("address");
        String from = params.get("from");
        String to = params.get("to");

        return semuxApi.getAccountTransactions(address, from, to);
    }

    /**
     * GET /get_transaction?hash
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse getTransaction(Map<String, String> params) {
        String hash = params.get("hash");
        return semuxApi.getTransaction(hash);
    }

    /**
     * GET /send_transaction?raw
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse sendTransaction(Map<String, String> params) {
        String raw = params.get("raw");
        return semuxApi.sendTransaction(raw);
    }

    /**
     * GET /get_account?address
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse getAccount(Map<String, String> params) {
        String address = params.get("address");
        return semuxApi.getAccount(address);
    }

    /**
     * GET /add_to_blacklist?ip
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse addToBlackList(Map<String, String> params) {
        String ip = params.get("ip");
        return semuxApi.addToBlacklist(ip);
    }

    /**
     * GET /add_to_whitelist?ip
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse addToWhiteList(Map<String, String> params) {
        String ip = params.get("ip");
        return semuxApi.addToWhitelist(ip);
    }

    /**
     * GET /get_latest_block_number
     *
     * @return
     */
    private ApiHandlerResponse getLatestBlockNumber() {
        return semuxApi.getLatestBlockNumber();
    }

    /**
     * GET /get_latest_block
     *
     * @return
     */
    private ApiHandlerResponse getLatestBlock() {
        return semuxApi.getLatestBlock();
    }

    /**
     * GET /get_delegate?address
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse getDelegate(Map<String, String> params) {
        String address = params.get("address");
        return semuxApi.getDelegate(address);
    }

    /**
     * GET /get_validators
     *
     * @return
     */
    private ApiHandlerResponse getValidators() {
       return semuxApi.getValidators();
    }

    /**
     * GET /get_delegates
     *
     * @return
     */
    private ApiHandlerResponse getDelegates() {
        return semuxApi.getDelegates();
    }

    /**
     * GET /get_vote?voter&delegate
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse getVote(Map<String, String> params) {
        String voter = params.get("voter");
        String delegate = params.get("delegate");
        return semuxApi.getVotes(delegate, voter);
    }

    /**
     * GET /get_votes?delegate
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse getVotes(Map<String, String> params) {
        String delegate = params.get("delegate");
        return semuxApi.getVotes(delegate);
    }

    /**
     * GET /list_accounts
     *
     * @return
     */
    private ApiHandlerResponse listAccounts() {
       return semuxApi.listAccounts();
    }

    /**
     * GET /create_account
     *
     * @return
     */
    private ApiHandlerResponse createAccount() {
        return semuxApi.createAccount();
    }

    /**
     * GET /get_transaction_limits
     *
     * @param params
     * @return
     */
    private ApiHandlerResponse getTransactionLimits(Map<String, String> params) {
        return semuxApi.getTransactionLimits(params.get("type"));
    }

    /**
     * This method processes the following transaction-related endpoints:
     *
     * <ul>
     * <li>GET /transfer?from&to&value&fee&data</li>
     * <li>GET /delegate?from&fee&data</li>
     * <li>GET /vote?from&to&value&fee&data</li>
     * <li>GET /unvote?from&to&value&fee&data</li>
     * </ul>
     *
     * @param cmd
     *            type of transaction
     * @param params
     * @return
     */
    private ApiHandlerResponse doTransaction(Command cmd, Map<String, String> params) {
        // [1] check if kernel.getWallet().is unlocked
        if (!kernel.getWallet().isUnlocked()) {
            return semuxApi.failure("Wallet is locked");
        }

        String from = params.get("from");
        String to = params.get("to");
        String value = params.get("value");
        String fee = params.get("fee");
        String data = params.get("data");

        // [2] parse transaction type
        switch (cmd) {
        case TRANSFER:
            return semuxApi.transfer(value,from,to,fee,data);
        case DELEGATE:
            return semuxApi.registerDelegate(from,fee,data);
        case VOTE:
            return semuxApi.vote(from,to,value,fee);
        case UNVOTE:
            return semuxApi.unvote(from,to,value,fee);
        default:
            return semuxApi.failure("Unsupported transaction type: " + cmd.toString());
        }
    }
}
