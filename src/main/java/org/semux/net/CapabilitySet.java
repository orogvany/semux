/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.net;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An immutable set of capabilities.
 */
public class CapabilitySet {

    private final Set<String> capabilities;

    private CapabilitySet(Set<String> capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Creates an empty and immutable ${@link CapabilitySet}
     *
     * @return an empty and immutable ${@link CapabilitySet}
     */
    public static CapabilitySet emptySet() {
        return new CapabilitySet(Collections.emptySet());
    }

    /**
     * Converts a list of ${@link Capability} into a ${@link CapabilitySet}.
     *
     * @param capabilityList
     *            a list of ${@link Capability}
     * @return a ${@link CapabilitySet} contains the provided list of
     *         ${@link Capability}
     */
    public static CapabilitySet of(Capability... capabilityList) {
        return new CapabilitySet(Arrays.stream(capabilityList).map(Capability::toString)
                .collect(Collectors.toSet()));
    }

    /**
     * Converts a list of String into a ${@link CapabilitySet}.
     *
     * @param capabilityList
     *            a list of ${@link String}
     * @return a ${@link CapabilitySet} contains the provided list of
     *         ${@link String}.
     */
    public static CapabilitySet of(String... capabilityList) {
        return new CapabilitySet(Arrays.stream(capabilityList).collect(Collectors.toSet()));
    }

    /**
     * Checks whether the capability is supported by the ${@link CapabilitySet}.
     *
     * @param capability
     *            the ${@link Capability} to be checked.
     * @return true if the capability is supported, false if not
     */
    public boolean isSupported(Capability capability) {
        return capabilities.contains(capability.toString());
    }

    /**
     *
     * @return the number of capabilities in the ${@link CapabilitySet}.
     */
    public int size() {
        return capabilities.size();
    }

    /**
     * Converts the ${@link CapabilitySet} to a list of String.
     *
     * @return a list of capabilities in ${@link String}
     */
    public List<String> toList() {
        return capabilities.stream().sorted().collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object object) {
        return object instanceof CapabilitySet && capabilities.equals(((CapabilitySet) object).capabilities);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(capabilities);
    }
}
