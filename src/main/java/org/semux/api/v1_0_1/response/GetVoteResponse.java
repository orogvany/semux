/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.api.v1_0_1.response;

import org.semux.api.v1_0_1.ApiHandlerResponse;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @deprecated
 */
public class GetVoteResponse extends ApiHandlerResponse {

    @JsonProperty("result")
    public final Long vote;

    public GetVoteResponse(
            @JsonProperty("success") Boolean success,
            @JsonProperty("result") Long vote) {
        super(success, null);
        this.vote = vote;
    }
}
