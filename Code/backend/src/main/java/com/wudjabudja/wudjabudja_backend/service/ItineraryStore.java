package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.ItineraryResponse;
import com.wudjabudja.wudjabudja_backend.model.ItineraryOption;
import com.wudjabudja.wudjabudja_backend.model.ItineraryOptionRepository;
import com.wudjabudja.wudjabudja_backend.model.TripRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Saves Gemini's itinerary as itinerary_options rows and reads it back as a tree (MVP-07).
@Service
public class ItineraryStore {

    private static final List<String> BLOCK_ORDER = List.of("morning", "afternoon", "evening");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})");

    // Column limits from schema.sql. Gemini rarely goes over, but a long name shouldn't sink a whole save.
    private static final int MAX_TITLE = 200;
    private static final int MAX_NEIGHBORHOOD = 100;

    private final ItineraryOptionRepository repository;
    private final TripRepository trips;

    public ItineraryStore(ItineraryOptionRepository repository, TripRepository trips) {
        this.repository = repository;
        this.trips = trips;
    }

    // Swap the trip's old options for new ones. @Transactional: if any insert fails,
    // the delete is undone too, so the trip never ends up half-saved.
    @Transactional
    public ItineraryResponse replaceForTrip(Long tripId, Itinerary itinerary) {
        // Gemini takes minutes, so two Generate clicks can finish close together. Locking the trip
        // makes the second save wait, then delete the first one's rows, instead of keeping both sets.
        trips.lockById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + tripId + " not found"));
        repository.deleteByTripId(tripId);
        List<ItineraryOption> saved = repository.saveAll(flatten(tripId, itinerary, OffsetDateTime.now()));
        return rebuild(tripId, saved);
    }

    // The trip's saved itinerary, or empty if it hasn't been generated yet
    @Transactional(readOnly = true)
    public Optional<ItineraryResponse> loadForTrip(Long tripId) {
        List<ItineraryOption> rows = repository.findByTripIdOrderByIdAsc(tripId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rebuild(tripId, rows));
    }

    // ---------- tree -> rows ----------

    // One row per option. Each row gets a copy of its day's date, its block, and its subgroup.
    static List<ItineraryOption> flatten(Long tripId, Itinerary itinerary, OffsetDateTime generatedAt) {
        List<ItineraryOption> rows = new ArrayList<>();
        for (Itinerary.Day day : orEmpty(itinerary.days())) {
            LocalDate date = parseDate(day.date());
            for (Itinerary.Block block : orEmpty(day.blocks())) {
                for (Itinerary.Subgroup sub : orEmpty(block.subgroups())) {
                    for (Itinerary.Option opt : orEmpty(sub.options())) {
                        ItineraryOption row = new ItineraryOption();
                        row.setTripId(tripId);
                        row.setDate(date);
                        row.setTimeBlock(block.timeBlock());
                        row.setSubgroupParticipants(new ArrayList<>(orEmpty(sub.subgroupParticipants())));
                        row.setSplitReason(sub.splitReason());
                        row.setTitle(cut(opt.title() == null ? "Untitled option" : opt.title(), MAX_TITLE));
                        row.setDescription(opt.description() == null ? "" : opt.description());   // NOT NULL column
                        row.setCategory(opt.category());
                        row.setNeighborhood(opt.neighborhood() == null ? null : cut(opt.neighborhood(), MAX_NEIGHBORHOOD));
                        row.setStartTime(parseTime(opt.startTime()));
                        row.setEndTime(parseTime(opt.endTime()));
                        row.setEstimatedCostUsd(opt.estimatedCostUsd() == null ? BigDecimal.ZERO : opt.estimatedCostUsd());
                        row.setWhyItFits(opt.whyItFits());
                        row.setCreatedAt(generatedAt);   // same timestamp on every row = one "generation"
                        rows.add(row);
                    }
                }
            }
        }
        return rows;
    }

    // ---------- rows -> tree ----------

    // Days in date order, blocks morning -> afternoon -> evening, subgroups and options in insert (id) order.
    static ItineraryResponse rebuild(Long tripId, List<ItineraryOption> rows) {
        List<ItineraryOption> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(ItineraryOption::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        // date -> time_block -> subgroup -> options. TreeMap sorts dates; LinkedHashMap keeps first-seen order.
        Map<LocalDate, Map<String, Map<SubgroupKey, List<ItineraryResponse.Option>>>> tree = new TreeMap<>();
        OffsetDateTime generatedAt = null;

        for (ItineraryOption row : sorted) {
            SubgroupKey key = new SubgroupKey(orEmpty(row.getSubgroupParticipants()), row.getSplitReason());
            tree.computeIfAbsent(row.getDate(), d -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getTimeBlock(), b -> new LinkedHashMap<>())
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(toOption(row));
            if (row.getCreatedAt() != null && (generatedAt == null || row.getCreatedAt().isAfter(generatedAt))) {
                generatedAt = row.getCreatedAt();
            }
        }

        List<ItineraryResponse.Day> days = new ArrayList<>();
        tree.forEach((date, blocks) -> {
            List<String> blockNames = new ArrayList<>(blocks.keySet());
            blockNames.sort(Comparator.comparingInt(ItineraryStore::blockRank));

            List<ItineraryResponse.Block> blockList = new ArrayList<>();
            for (String name : blockNames) {
                List<ItineraryResponse.Subgroup> subgroups = new ArrayList<>();
                blocks.get(name).forEach((key, options) ->
                        subgroups.add(new ItineraryResponse.Subgroup(key.participants(), key.splitReason(), options)));
                blockList.add(new ItineraryResponse.Block(name, subgroups));
            }
            days.add(new ItineraryResponse.Day(date.toString(), blockList));
        });

        // Always UTC, so a fresh POST and a later GET print the same timestamp
        String generated = generatedAt == null ? null : generatedAt.withOffsetSameInstant(ZoneOffset.UTC).toString();
        return new ItineraryResponse(tripId, generated, days);
    }

    // Rows in the same block belong to the same subgroup when they have the same people and reason
    private record SubgroupKey(List<Long> participants, String splitReason) {}

    private static ItineraryResponse.Option toOption(ItineraryOption row) {
        return new ItineraryResponse.Option(
                row.getId(),
                row.getTitle(),
                row.getDescription(),
                row.getCategory(),
                row.getNeighborhood(),
                row.getStartTime() == null ? null : row.getStartTime().format(HH_MM),
                row.getEndTime() == null ? null : row.getEndTime().format(HH_MM),
                // Always 2 decimals (15.00) like the NUMERIC(10, 2) column, so POST and GET match
                row.getEstimatedCostUsd() == null ? null : row.getEstimatedCostUsd().setScale(2, RoundingMode.HALF_UP),
                row.getWhyItFits(),
                row.isFinal());
    }

    // ---------- small helpers ----------

    private static int blockRank(String timeBlock) {
        int i = BLOCK_ORDER.indexOf(timeBlock);
        return i < 0 ? BLOCK_ORDER.size() : i;   // anything unexpected goes last
    }

    // GeminiService already checked the dates, so a bad one here means something is really off
    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini returned an unreadable date: " + date);
        }
    }

    // "09:00", "9:00" or "09:00:00" -> 09:00. Anything else -> null (the time columns allow null).
    static LocalTime parseTime(String text) {
        if (text == null) return null;
        Matcher m = TIME.matcher(text.trim());
        if (!m.find()) return null;
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        return hour < 24 && minute < 60 ? LocalTime.of(hour, minute) : null;
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
