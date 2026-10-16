/*
    This file is part of Go.dot remote — https://github.com/pob31/go.dot-remote
    Copyright (C) 2026 Pierre-Olivier Boulant
    Go.dot remote is free software: you can redistribute it and/or modify it under the
    terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later
    version. Go.dot remote is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
    (LICENSE, at the repository root) for more details.
    SPDX-License-Identifier: GPL-3.0-or-later
*/

package com.pob31.godot.remote.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a typed or scanned Choufleur address becomes, and what is refused. */
class PromptAddressTest {

    @Test
    fun aWholeAddressIsKeptAsItIs() {
        assertEquals("http://192.168.1.40:8080/list/conduite-son", PromptAddress.normalise("http://192.168.1.40:8080/list/conduite-son"))
        assertEquals("https://prompt.example:8443/list/lx", PromptAddress.normalise("https://prompt.example:8443/list/lx"))
        // A port the operator wrote stays, even when it is the scheme's own.
        assertEquals("http://10.0.0.5:80/", PromptAddress.normalise("http://10.0.0.5:80/"))
    }

    @Test
    fun spacesAroundAndTheSchemesCaseDoNotMatter() {
        assertEquals("http://192.168.1.40:8080/list/a", PromptAddress.normalise("  HTTP://192.168.1.40:8080/list/a \n"))
        assertEquals("http://studio.lan:8080/", PromptAddress.normalise("http://Studio.LAN:8080"))
    }

    @Test
    fun aWholeAddressWithNoPathGetsTheRoot() {
        assertEquals("http://192.168.1.40:8080/", PromptAddress.normalise("http://192.168.1.40:8080"))
        assertEquals("http://192.168.1.40/", PromptAddress.normalise("http://192.168.1.40"))
    }

    @Test
    fun aBareHostIsCompletedToChoufleursPort() {
        assertEquals("http://192.168.1.40:8080/", PromptAddress.normalise("192.168.1.40"))
        assertEquals("http://regie-mac:8080/", PromptAddress.normalise("regie-mac"))
    }

    @Test
    fun aHostAndPortKeepTheirPort() {
        assertEquals("http://192.168.1.40:8081/", PromptAddress.normalise("192.168.1.40:8081"))
    }

    @Test
    fun aHostAndPathGetThePortAndKeepThePath() {
        assertEquals("http://192.168.1.40:8080/list/conduite-son", PromptAddress.normalise("192.168.1.40/list/conduite-son"))
        assertEquals("http://192.168.1.40:9000/list/x?y=1#z", PromptAddress.normalise("192.168.1.40:9000/list/x?y=1#z"))
    }

    @Test
    fun anIpv6HostInBracketsIsAHost() {
        assertEquals("http://[fe80::1]:8080/", PromptAddress.normalise("[fe80::1]"))
        assertEquals("http://[fe80::1]:8081/list/a", PromptAddress.normalise("http://[fe80::1]:8081/list/a"))
    }

    @Test
    fun whatIsNotAnAddressIsRefused() {
        for (text in listOf(
            "", "   ", "ws://192.168.1.40:8080/", "ftp://host/", "chrome://flags", "javascript:alert(1)",
            "http://", "http:///list/a", "http://host:/", "http://host:99999/", "host:0", "host:abc",
            "192.168.1.40 8080", "http://a b/", "http://user:pass@host:8080/", "http://bad_host:8080/",
        )) {
            assertNull("'$text' is not an address", PromptAddress.normalise(text))
        }
    }

    @Test
    fun aDotLocalNameIsSaidToBeOne() {
        assertTrue(PromptAddress.isLocalName("http://choufleur.local:8080/list/a"))
        assertTrue(PromptAddress.isLocalName("http://Regie-Mac.LOCAL:8080/"))
        assertFalse(PromptAddress.isLocalName("http://192.168.1.40:8080/"))
        assertFalse(PromptAddress.isLocalName("http://local:8080/"))
    }

    @Test
    fun theMachineIsSchemeAndHostWhateverThePort() {
        assertEquals("http://192.168.1.40", PromptAddress.machineOf("http://192.168.1.40:8080/list/a"))
        assertEquals("https://h", PromptAddress.machineOf("HTTPS://H/"))
        // The durable /list/<id> on 8080 redirects to the show server on 8081: the same machine.
        assertEquals(PromptAddress.machineOf("http://172.22.1.27:8080/list/cues"), PromptAddress.machineOf("http://172.22.1.27:8081/?list=cues"))
        assertFalse(PromptAddress.machineOf("http://172.22.1.28:8081/") == PromptAddress.machineOf("http://172.22.1.27:8080/"))
        assertFalse(PromptAddress.machineOf("https://172.22.1.27:8081/") == PromptAddress.machineOf("http://172.22.1.27:8080/"))
    }
}
