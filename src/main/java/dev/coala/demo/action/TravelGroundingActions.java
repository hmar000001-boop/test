package dev.coala.demo.action;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** Mock travel-domain grounding actions. They never access a real calendar, airline or hotel. */
public final class TravelGroundingActions {
    private TravelGroundingActions() { }

    public static GroundingAction checkCalendar() {
        return action("check_calendar", "Check the mock calendar before searching travel inventory.", Set.of(), Set.of(), ActionRisk.LOW,
                (call, context) -> {
                    Map<String, Object> calendar = Map.of("window", "2026-11-05 to 2026-11-08", "conflicts", "none in fixture", "status", "simulated");
                    context.workingMemory().put("travel.calendar", calendar);
                    context.workingMemory().put("travel.calendar.checked", true);
                    return ActionResult.continueWith("Mock calendar checked: the illustrative 2026-11-05 to 2026-11-08 window has no seeded conflict. Dates are a fixture, not a live calendar result.");
                });
    }

    public static GroundingAction searchFlights() {
        return action("search_flights", "Search synthetic flight options after the calendar check.", Set.of("destination"), Set.of("origin"), ActionRisk.LOW,
                (call, context) -> {
                    if (!Boolean.parseBoolean(context.workingMemory().getString("travel.calendar.checked", "false"))) {
                        return ActionResult.continueWith("needs_replan: calendar availability must be checked before flight search; no provider query was made.");
                    }
                    String destination = call.argument("destination", "Paris");
                    String origin = call.argument("origin", "YUL");
                    List<Map<String, Object>> options = List.of(
                            Map.of("name", "Fixture flight A", "route", origin + " to " + destination, "priceCad", 640, "detail", "round trip; sample fare"),
                            Map.of("name", "Fixture flight B", "route", origin + " to " + destination, "priceCad", 710, "detail", "round trip; sample fare"));
                    context.workingMemory().put("travel.flight_options", options);
                    context.workingMemory().put("travel.destination", destination);
                    return ActionResult.continueWith("Mock flight search returned two synthetic round-trip fares for " + destination + ": CAD 640 and CAD 710. No airline was contacted.");
                });
    }

    public static GroundingAction searchHotels() {
        return action("search_hotels", "Search synthetic lodging options after the calendar check.", Set.of("destination"), Set.of("checkIn", "nights"), ActionRisk.LOW,
                (call, context) -> {
                    if (!Boolean.parseBoolean(context.workingMemory().getString("travel.calendar.checked", "false"))) {
                        return ActionResult.continueWith("needs_replan: calendar availability must be checked before hotel search; no provider query was made.");
                    }
                    String destination = call.argument("destination", context.workingMemory().getString("travel.destination", "Paris"));
                    String checkIn = call.argument("checkIn", "2026-11-05");
                    String nights = call.argument("nights", "3");
                    List<Map<String, Object>> options = List.of(
                            Map.of("name", "Fixture hotel A", "destination", destination, "checkIn", checkIn, "nights", nights, "totalCad", 390, "detail", "sample total"),
                            Map.of("name", "Fixture hotel B", "destination", destination, "checkIn", checkIn, "nights", nights, "totalCad", 460, "detail", "sample total"));
                    context.workingMemory().put("travel.hotel_options", options);
                    return ActionResult.continueWith("Mock hotel search returned two synthetic lodging totals: CAD 390 and CAD 460. No property was contacted.");
                });
    }

    public static GroundingAction draftItinerary() {
        return action("draft_itinerary", "Combine calendar, flight and hotel observations into a non-binding travel proposal and compare it with the stated budget cap.", Set.of(), Set.of("budgetCad"), ActionRisk.LOW,
                (call, context) -> {
                    if (!Boolean.parseBoolean(context.workingMemory().getString("travel.calendar.checked", "false"))
                            || context.workingMemory().get("travel.flight_options").isEmpty()
                            || context.workingMemory().get("travel.hotel_options").isEmpty()) {
                        return ActionResult.continueWith("needs_replan: the draft requires calendar, flight and hotel observations; no itinerary was finalized.");
                    }
                    String destination = context.workingMemory().getString("travel.destination", "Paris");
                    Object calendarObject = context.workingMemory().get("travel.calendar").orElse(Map.of());
                    Object flightsObject = context.workingMemory().get("travel.flight_options").orElse(List.of());
                    Object hotelsObject = context.workingMemory().get("travel.hotel_options").orElse(List.of());
                    int flight = minAmount(flightsObject, "priceCad", 640);
                    int hotel = minAmount(hotelsObject, "totalCad", 390);
                    int local = 160;
                    int total = flight + hotel + local;
                    int budget;
                    try { budget = Integer.parseInt(call.argument("budgetCad", "1400")); }
                    catch (NumberFormatException ex) { throw new IllegalArgumentException("budgetCad must be a whole number of CAD", ex); }
                    if (budget < 1 || budget > 1_000_000) throw new IllegalArgumentException("budgetCad must be between 1 and 1000000");
                    boolean withinBudget = total <= budget;
                    String window = calendarObject instanceof Map<?, ?> map && map.get("window") != null ? String.valueOf(map.get("window")) : "dates unconfirmed";
                    String itinerary = "Proposed trip to " + destination + "\n"
                            + "Date window: " + window + " (mock calendar fixture)\n"
                            + "Flight: lowest fixture fare CAD " + flight + "\n"
                            + "Hotel: lowest fixture total CAD " + hotel + "\n"
                            + "Local transport/food estimate: CAD " + local + "\n"
                            + "Illustrative subtotal: CAD " + total + "\n"
                            + "Budget cap: CAD " + budget + "\n"
                            + "Budget check: " + (withinBudget ? "WITHIN budget" : "OVER budget") + " (computed from fixture totals)\n"
                            + "Calendar conflicts: none in the fixture; real availability is unverified.\n"
                            + "Safety boundary: this is a draft only. No booking, payment or external account change occurred.";
                    context.workingMemory().put("travel.proposed_itinerary", itinerary);
                    context.workingMemory().put("travel.estimate_cad", total);
                    context.workingMemory().put("travel.budget_cad", budget);
                    context.workingMemory().put("travel.within_budget", withinBudget);
                    return ActionResult.continueWith("Assembled a draft itinerary from working-memory observations; illustrative subtotal CAD " + total + ". No reservation was made.");
                });
    }

    public static GroundingAction presentItinerary() {
        return action("present_itinerary", "Return the draft itinerary to the user without making any reservation.", Set.of(), Set.of(), ActionRisk.LOW,
                (call, context) -> ActionResult.finish(context.workingMemory().getString("travel.proposed_itinerary",
                        "No itinerary could be prepared because required calendar/flight/hotel observations are missing.")));
    }

    private static int minAmount(Object raw, String key, int fallback) {
        if (!(raw instanceof List<?> list)) return fallback;
        int min = Integer.MAX_VALUE;
        for (Object item : list) if (item instanceof Map<?, ?> map && map.get(key) instanceof Number number) min = Math.min(min, number.intValue());
        return min == Integer.MAX_VALUE ? fallback : min;
    }

    private static GroundingAction action(String id, String description, Set<String> required, Set<String> optional,
                                          ActionRisk risk, BiFunction<ActionCall, AgentContext, ActionResult> operation) {
        return new GroundingAction() {
            @Override public String id() { return id; }
            @Override public String description() { return description; }
            @Override public Set<String> requiredArguments() { return required; }
            @Override public Set<String> optionalArguments() { return optional; }
            @Override public ActionRisk risk() { return risk; }
            @Override public List<ActionArgumentDefinition> argumentDefinitions() {
                List<ActionArgumentDefinition> defs = new ArrayList<>();
                required.stream().sorted().forEach(key -> defs.add(ActionArgumentDefinition.requiredString(key, key)));
                optional.stream().sorted().forEach(key -> defs.add(ActionArgumentDefinition.optionalString(key, key)));
                return List.copyOf(defs);
            }
            @Override public boolean idempotent() { return true; }
            @Override public ActionResult execute(ActionCall call, AgentContext context) { return operation.apply(call, context); }
        };
    }
}
