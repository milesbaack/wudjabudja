package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.AddParticipantRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Checks add-traveler request validation and name derivation without a database (GRP-02).
class ParticipantServiceTest {

    @Test
    void validEmailHasNoProblems() {
        assertEquals(List.of(), ParticipantService.problems(new AddParticipantRequest("alex@example.com")));
        assertEquals(List.of(), ParticipantService.problems(new AddParticipantRequest("alex.smith@school.edu", "Alex Smith")));
    }

    @Test
    void missingOrMalformedEmailIsRejected() {
        assertEquals(List.of("email is required"), ParticipantService.problems(null));
        assertEquals(List.of("email is required"), ParticipantService.problems(new AddParticipantRequest("   ")));
        assertEquals(List.of("email must be a valid email address"), ParticipantService.problems(new AddParticipantRequest("not-an-email")));
        assertEquals(List.of("email must be a valid email address"), ParticipantService.problems(new AddParticipantRequest("@example.com")));
        assertEquals(List.of("email must be a valid email address"), ParticipantService.problems(new AddParticipantRequest("alex@localhost")));
    }

    @Test
    void derivesReadableNameFromEmailPrefix() {
        assertEquals("Alex", ParticipantService.deriveNameFromEmail("alex@example.com"));
        assertEquals("Alex smith", ParticipantService.deriveNameFromEmail("alex.smith@example.com"));
    }
}
