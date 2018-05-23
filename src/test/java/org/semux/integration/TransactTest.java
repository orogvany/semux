/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.semux.core.Amount.Unit.NANO_SEM;
import static org.semux.core.Amount.Unit.SEM;
import static org.semux.core.Amount.ZERO;
import static org.semux.core.Amount.sub;
import static org.semux.core.Amount.sum;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.semux.IntegrationTest;
import org.semux.Kernel;
import org.semux.Kernel.State;
import org.semux.KernelMock;
import org.semux.api.v1_0_1.response.DoTransactionResponse;
import org.semux.api.v1_0_1.response.GetAccountResponse;
import org.semux.api.v1_0_1.response.GetAccountTransactionsResponse;
import org.semux.api.v1_0_1.response.GetDelegateResponse;
import org.semux.api.v1_0_1.response.Types;
import org.semux.core.Amount;
import org.semux.core.Genesis;
import org.semux.core.TransactionType;
import org.semux.core.state.Delegate;
import org.semux.crypto.Hex;
import org.semux.net.NodeManager;
import org.semux.net.NodeManager.Node;
import org.semux.net.SemuxChannelInitializer;
import org.semux.rules.KernelRule;
import org.semux.util.ApiClient;
import org.semux.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

// TODO: migrate to API v2
@SuppressWarnings("deprecation")
@Category(IntegrationTest.class)
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Genesis.class, NodeManager.class })
@PowerMockIgnore({ "jdk.internal.*", "javax.management.*" })
public class TransactTest {

    private static Logger logger = LoggerFactory.getLogger(TransactTest.class);

    private static final Amount PREMINE = SEM.of(5000);

    @Rule
    KernelRule kernelRuleValidator1 = new KernelRule(51610, 51710);

    @Rule
    KernelRule kernelRuleValidator2 = new KernelRule(51620, 51720);

    @Rule
    KernelRule kernelRulePremine = new KernelRule(51630, 51730);

    @Rule
    KernelRule kernelRuleReceiver = new KernelRule(51640, 51740);

    public KernelMock kernelValidator1; // validator node
    public KernelMock kernelValidator2; // validator node
    public KernelMock kernelPremine; // normal node with balance
    public KernelMock kernelReceiver; // normal node with no balance

    @Before
    public void setUp() throws Exception {
        // prepare kernels
        kernelRuleValidator1.speedUpConsensus();
        kernelRuleValidator2.speedUpConsensus();
        kernelRulePremine.speedUpConsensus();
        kernelRuleReceiver.speedUpConsensus();
        kernelValidator1 = kernelRuleValidator1.getKernel();
        kernelValidator2 = kernelRuleValidator2.getKernel();
        kernelPremine = kernelRulePremine.getKernel();
        kernelReceiver = kernelRuleReceiver.getKernel();

        // mock genesis.json
        Genesis genesis = mockGenesis();
        mockStatic(Genesis.class);
        when(Genesis.load(any())).thenReturn(genesis);

        // mock seed nodes
        Set<Node> nodes = new HashSet<>();
        nodes.add(new Node(kernelValidator1.getConfig().p2pListenIp(), kernelValidator1.getConfig().p2pListenPort()));
        nodes.add(new Node(kernelValidator2.getConfig().p2pListenIp(), kernelValidator2.getConfig().p2pListenPort()));

        // start kernels
        kernelValidator1.start();
        kernelValidator2.start();
        kernelPremine.start();
        kernelReceiver.start();

        List<Kernel> kernels = new ArrayList<>();
        kernels.add(kernelValidator1);
        kernels.add(kernelValidator2);
        kernels.add(kernelPremine);
        kernels.add(kernelReceiver);

        // connect to each other
        for (Kernel kernel : kernels) {
            for (Node node : nodes) {
                SemuxChannelInitializer ci = new SemuxChannelInitializer(kernel, node);
                kernel.getClient().connect(node, ci);
            }
        }

        // wait for kernels
        await().atMost(20, SECONDS).until(() -> kernelValidator1.state() == State.RUNNING
                && kernelValidator2.state() == State.RUNNING
                && kernelPremine.state() == State.RUNNING
                && kernelReceiver.state() == State.RUNNING
                && kernelReceiver.getChannelManager().getActivePeers().size() >= 2);
    }

    @After
    public void tearDown() {
        // stop kernels
        kernelValidator1.stop();
        kernelValidator2.stop();
        kernelPremine.stop();
        kernelReceiver.stop();
    }

    @Test
    public void testTransfer() throws IOException {
        final Amount value = SEM.of(1000);
        final Amount fee = kernelPremine.getConfig().minTransactionFee();

        // prepare transaction
        HashMap<String, Object> params = new HashMap<>();
        params.put("from", coinbaseOf(kernelPremine));
        params.put("to", coinbaseOf(kernelReceiver));
        params.put("value", String.valueOf(value.getNano()));
        params.put("fee", String.valueOf(fee.getNano()));

        // send transaction
        logger.info("Making transfer request", params);
        DoTransactionResponse response = new ObjectMapper().readValue(
                kernelPremine.getApiClient().request("transfer", params),
                DoTransactionResponse.class);
        assertTrue(response.success);

        // wait for transaction to be processed
        logger.info("Waiting for the transaction to be processed...");
        await().atMost(20, SECONDS).until(availableOf(kernelPremine, coinbaseOf(kernelPremine)),
                equalTo(sub(PREMINE, sum(value, fee))));
        await().atMost(20, SECONDS).until(availableOf(kernelReceiver, coinbaseOf(kernelReceiver)),
                equalTo(value));

        // assert that the transaction has been recorded across nodes
        assertLatestTransaction(kernelPremine, coinbaseOf(kernelPremine),
                TransactionType.TRANSFER, coinbaseOf(kernelPremine), coinbaseOf(kernelReceiver), value, fee,
                Bytes.EMPTY_BYTES);
        assertLatestTransaction(kernelReceiver, coinbaseOf(kernelReceiver),
                TransactionType.TRANSFER, coinbaseOf(kernelPremine), coinbaseOf(kernelReceiver), value, fee,
                Bytes.EMPTY_BYTES);

        // assert the state
        List<Delegate> delegates = kernelPremine.getBlockchain().getDelegateState().getDelegates();
        assertThat(delegates).anySatisfy((d) -> Arrays.equals(d.getAddress(), coinbaseOf(kernelPremine)));
    }

    @Test
    public void testDelegate() throws IOException {
        final Amount fee = kernelPremine.getConfig().minTransactionFee();

        // prepare transaction
        HashMap<String, Object> params = new HashMap<>();
        params.put("from", coinbaseOf(kernelPremine));
        params.put("fee", fee.getNano());
        params.put("data", Bytes.of("test"));

        // send transaction
        logger.info("Making delegate request", params);
        DoTransactionResponse response = new ObjectMapper().readValue(
                kernelPremine.getApiClient().request("delegate", params),
                DoTransactionResponse.class);
        assertTrue(response.success);

        // wait for transaction processing
        logger.info("Waiting for the transaction to be processed...");
        await().atMost(20, SECONDS).until(availableOf(kernelPremine, coinbaseOf(kernelPremine)),
                equalTo(sub(PREMINE, sum(kernelPremine.getConfig().minDelegateBurnAmount(), fee))));

        // assert that the transaction has been recorded across nodes
        assertLatestTransaction(kernelPremine, coinbaseOf(kernelPremine),
                TransactionType.DELEGATE, coinbaseOf(kernelPremine), Bytes.EMPTY_ADDRESS,
                kernelPremine.getConfig().minDelegateBurnAmount(), fee, Bytes.of("test"));

        // assert that the number of votes has been recorded into delegate state
        assertDelegate(kernelPremine, kernelPremine.getCoinbase().toAddress(), ZERO);
    }

    @Test
    public void testVote() throws IOException {
        final Amount fee = kernelPremine.getConfig().minTransactionFee();
        final Amount votes = SEM.of(100);
        final Amount votesWithFee = sum(votes, fee);

        // prepare transaction
        HashMap<String, Object> params = new HashMap<>();
        params.put("from", coinbaseOf(kernelPremine));
        params.put("to", coinbaseOf(kernelValidator1));
        params.put("value", votes.getNano());
        params.put("fee", fee.getNano());

        // send vote transaction
        logger.info("Making vote request", params);
        DoTransactionResponse voteResponse = new ObjectMapper().readValue(
                kernelPremine.getApiClient().request("vote", params),
                DoTransactionResponse.class);
        assertTrue(voteResponse.success);

        // wait for the vote transaction to be processed
        logger.info("Waiting for the vote transaction to be processed...");
        await().atMost(20, SECONDS).until(availableOf(kernelPremine, coinbaseOf(kernelPremine)),
                equalTo(sub(PREMINE, votesWithFee)));

        // assert that the vote transaction has been recorded across nodes
        assertLatestTransaction(kernelPremine, coinbaseOf(kernelPremine),
                TransactionType.VOTE, coinbaseOf(kernelPremine), coinbaseOf(kernelValidator1),
                votes, fee, Bytes.EMPTY_BYTES);

        // assert that the number of votes has been recorded into the delegate state
        assertDelegate(kernelValidator1, kernelValidator1.getCoinbase().toAddress(), votes);

        // send unvote transaction
        final Amount unvotes = SEM.of(50);
        logger.info("Making unvote request", params);
        params.put("from", coinbaseOf(kernelPremine));
        params.put("to", coinbaseOf(kernelValidator1));
        params.put("value", unvotes.getNano());
        params.put("fee", fee.getNano());
        DoTransactionResponse unvoteResponse = new ObjectMapper().readValue(
                kernelPremine.getApiClient().request("unvote", params),
                DoTransactionResponse.class);
        assertTrue(unvoteResponse.success);

        // wait for the vote transaction to be processed
        logger.info("Waiting for the unvote transaction to be processed...");
        await().atMost(20, SECONDS).until(availableOf(kernelPremine, coinbaseOf(kernelPremine)),
                equalTo(sum(sub(PREMINE, votesWithFee), sub(unvotes, fee))));

        // assert that the vote transaction has been recorded across nodes
        assertLatestTransaction(kernelPremine, coinbaseOf(kernelPremine),
                TransactionType.UNVOTE, coinbaseOf(kernelPremine), coinbaseOf(kernelValidator1),
                unvotes, fee, Bytes.EMPTY_BYTES);

        // assert that the number of votes has been recorded into the delegate state
        assertDelegate(kernelValidator1, kernelValidator1.getCoinbase().toAddress(), sub(votes, unvotes));
    }

    /**
     * Assert the latest transaction of the given address, by querying the specified
     * kernel.
     *
     * @param kernel
     * @param address
     * @param type
     * @param from
     * @param to
     * @param value
     * @param fee
     * @param data
     * @throws IOException
     */
    private void assertLatestTransaction(KernelMock kernel, byte[] address,
            TransactionType type, byte[] from, byte[] to, Amount value, Amount fee, byte[] data)
            throws IOException {
        Types.TransactionType result = latestTransactionOf(kernel, address);
        assertEquals(type.name(), result.type);
        assertEquals(Hex.encode0x(from), result.from);
        assertEquals(Hex.encode0x(to), result.to);
        assertEquals(value, NANO_SEM.of(result.value));
        assertEquals(fee, NANO_SEM.of(result.fee));
        assertEquals(Hex.encode0x(data), result.data);
    }

    /**
     * Assert that the address has be registered as a delegate.
     *
     * @param kernelMock
     * @param address
     * @param votes
     * @throws IOException
     */
    private void assertDelegate(KernelMock kernelMock, byte[] address, Amount votes) throws IOException {
        GetDelegateResponse getDelegateResponse = new ObjectMapper().readValue(
                kernelMock
                        .getApiClient()
                        .request("get_delegate", "address", Hex.encode0x(address)),
                GetDelegateResponse.class);
        assertTrue(getDelegateResponse.success);
        assertEquals(votes, NANO_SEM.of(getDelegateResponse.delegate.votes));
    }

    /**
     * Returns the callable which can be used to get the balance of given address.
     *
     * @param kernelMock
     * @param address
     * @return
     */
    private Callable<Amount> availableOf(KernelMock kernelMock, byte[] address) {
        return () -> {
            ApiClient apiClient = kernelMock.getApiClient();

            GetAccountResponse response = new ObjectMapper().readValue(
                    apiClient.request("get_account",
                            "address", address),
                    GetAccountResponse.class);

            return NANO_SEM.of(response.account.available);
        };
    }

    /**
     * Returns the nth transaction of the given address, by querify the specified
     * kernel.
     *
     * @param kernel
     * @param address
     * @return
     * @throws IOException
     */
    private Types.TransactionType latestTransactionOf(KernelMock kernel, byte[] address)
            throws IOException {
        ApiClient apiClient = kernel.getApiClient();

        GetAccountTransactionsResponse response = new ObjectMapper().readValue(
                apiClient.request("get_account_transactions",
                        "address", address,
                        "from", 0,
                        "to", 1000),
                GetAccountTransactionsResponse.class);

        return response.transactions.get(response.transactions.size() - 1);
    }

    /**
     * Returns the coinbase address of the given kernel.
     *
     * @param kernelMock
     * @return
     */
    private byte[] coinbaseOf(KernelMock kernelMock) {
        return kernelMock.getCoinbase().toAddress();
    }

    /**
     * Mocks a genesis instance where kernelValidator1 and kernelValidator2 are
     * validators and kernelPremine has some premined balance.
     *
     * @return
     */
    private Genesis mockGenesis() {
        // mock premine
        List<Genesis.Premine> premines = new ArrayList<>();
        premines.add(new Genesis.Premine(kernelPremine.getCoinbase().toAddress(), PREMINE, ""));

        // mock delegates
        HashMap<String, String> delegates = new HashMap<>();
        delegates.put("kernelValidator1", kernelValidator1.getCoinbase().toAddressString());
        delegates.put("kernelValidator2", kernelValidator2.getCoinbase().toAddressString());

        // mock genesis
        return Genesis.jsonCreator(0,
                "0x0000000000000000000000000000000000000000",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                1504742400000L,
                "semux",
                premines,
                delegates,
                new HashMap<>());
    }
}
