/**
 * Copyright (c) 2017 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.api.response;

import static org.semux.core.TransactionType.DELEGATE;

import org.semux.Kernel;
import org.semux.api.ApiHandlerResponse;
import org.semux.core.TransactionType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GetTransactionLimitsResponse extends ApiHandlerResponse {

    @JsonProperty("result")
    public final Result result;

    public GetTransactionLimitsResponse(Kernel kernel, TransactionType transactionType) {
        super(true, null);
        this.result = new Result(
                kernel.getConfig().maxTransactionDataSize(transactionType),
                kernel.getConfig().minTransactionFee(),
                transactionType.equals(DELEGATE) ? kernel.getConfig().minDelegateBurnAmount() : null);
    }

    @JsonCreator
    public GetTransactionLimitsResponse(
            @JsonProperty("success") Boolean success,
            @JsonProperty("result") Result result) {
        super(success, null);
        this.result = result;
    }

    public static class Result {

        @JsonProperty("maxTransactionDataSize")
        public final Integer maxTransactionDataSize;

        @JsonProperty("minTransactionFee")
        public final Long minTransactionFee;

        @JsonProperty("minDelegateBurnAmount")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public final Long minDelegateBurnAmount;

        @JsonCreator
        public Result(
                @JsonProperty("maxTransactionDataSize") Integer maxTransactionDataSize,
                @JsonProperty("minTransactionFee") Long minTransactionFee,
                @JsonProperty("minDelegateBurnAmount") Long minDelegateBurnAmount) {
            this.maxTransactionDataSize = maxTransactionDataSize;
            this.minTransactionFee = minTransactionFee;
            this.minDelegateBurnAmount = minDelegateBurnAmount;
        }
    }
}
