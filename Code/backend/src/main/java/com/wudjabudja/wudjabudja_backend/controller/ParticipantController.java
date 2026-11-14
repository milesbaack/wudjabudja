package com.wudjabudja.wudjabudja_backend.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/trips/{tripId}/participants")
public class ParticipantController {
    @PersistenceContext private EntityManager em;

    public record AddParticipantRequest(String email, @JsonProperty("full_name") String fullName) {}
    public record ParticipantResponse(@JsonProperty("user_id") Long userId, String email,
                                      @JsonProperty("full_name") String fullName, String role,
                                      @JsonProperty("quiz_saved") boolean quizSaved) {}
    private void checkTrip(Long tripId) {
        if (((Number) em.createNativeQuery("SELECT count(*) FROM trips WHERE id=:t")
                .setParameter("t", tripId).getSingleResult()).longValue() == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ParticipantResponse> participants(@PathVariable Long tripId) {
        checkTrip(tripId);
        List<?> rows = em.createNativeQuery("""
            SELECT u.id, u.email, u.full_name, p.role,
              EXISTS(SELECT 1 FROM user_preferences pref WHERE pref.trip_id=p.trip_id AND pref.user_id=u.id)
            FROM trip_participants p JOIN users u ON u.id=p.user_id
            WHERE p.trip_id=:t ORDER BY p.id
            """).setParameter("t", tripId).getResultList();
        List<ParticipantResponse> out = new ArrayList<>();
        for (Object row : rows) {
            Object[] r=(Object[])row;
            out.add(new ParticipantResponse(((Number)r[0]).longValue(), (String)r[1],
                    (String)r[2], (String)r[3], (Boolean)r[4]));
        }
        return out;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ParticipantResponse addParticipant(@PathVariable Long tripId,
                                              @RequestBody AddParticipantRequest req) {
        checkTrip(tripId);
        if (req == null || req.email() == null ||
                !req.email().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email required");
        String email=req.email().trim().toLowerCase(Locale.ROOT);
        String name=req.fullName()==null || req.fullName().isBlank()
                ? email.substring(0,email.indexOf('@')) : req.fullName().trim();
        // ON CONFLICT makes concurrent requests safe for an existing email.
        Object id=em.createNativeQuery("""
            INSERT INTO users(email,password_hash,full_name)
            VALUES (:email,:hash,:name)
            ON CONFLICT(email) DO UPDATE SET email=EXCLUDED.email
            RETURNING id
            """).setParameter("email",email).setParameter("hash","not-a-real-hash")
                .setParameter("name",name).getSingleResult();
        Long userId=((Number)id).longValue();
        int inserted=em.createNativeQuery("""
            INSERT INTO trip_participants(trip_id,user_id,role)
            VALUES (:t,:u,'member') ON CONFLICT(trip_id,user_id) DO NOTHING
            """).setParameter("t",tripId).setParameter("u",userId).executeUpdate();
        if(inserted==0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "That traveler is already on this trip");
        Object[] r=(Object[])em.createNativeQuery(
                        "SELECT email,full_name FROM users WHERE id=:u")
                .setParameter("u",userId).getSingleResult();
        return new ParticipantResponse(userId,(String)r[0],(String)r[1],"member",false);
    }

}
