/********************************************************************************
 * Copyright (c) 2022, 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package org.eclipse.jifa.gclog.parser;

import org.eclipse.jifa.common.domain.exception.ShouldNotReachHereException;
import org.eclipse.jifa.gclog.event.GCEvent;
import org.eclipse.jifa.gclog.event.ThreadEvent;
import org.eclipse.jifa.gclog.event.eventInfo.GCMemoryItem;
import org.eclipse.jifa.gclog.model.GCEventType;
import org.eclipse.jifa.gclog.model.GCModel;
import org.eclipse.jifa.gclog.model.GenZGCModel;
import org.eclipse.jifa.gclog.model.ZGCModel.ZStatistics;
import org.eclipse.jifa.gclog.model.ZGCModel.ZStatisticsItem;
import org.eclipse.jifa.gclog.util.GCLogUtil;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.jifa.gclog.event.eventInfo.MemoryArea.HEAP;
import static org.eclipse.jifa.gclog.event.eventInfo.MemoryArea.METASPACE;
import static org.eclipse.jifa.gclog.model.GCEventType.*;
import static org.eclipse.jifa.gclog.parser.ParseRule.ParseRuleContext.GCID;
import static org.eclipse.jifa.gclog.parser.ParseRule.ParseRuleContext.UPTIME;
import static org.eclipse.jifa.gclog.util.Constant.UNKNOWN_INT;

/**
 * Parser for Generational ZGC logs (Java 21+, JEP 474)
 * Generational ZGC introduces young (minor) and old (major) generation collections
 */
public class UnifiedGenZGCLogParser extends AbstractUnifiedGCLogParser {
    
    private static List<ParseRule> withGCIDRules;
    private static List<ParseRule> withoutGCIDRules;

    static {
        initializeParseRules();
    }

    private static void initializeParseRules() {
        withoutGCIDRules = new ArrayList<>(AbstractUnifiedGCLogParser.getSharedWithoutGCIDRules());
        withoutGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Allocation Stall", UnifiedGenZGCLogParser::parseAllocationStall));
        withoutGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Out Of Memory", UnifiedGenZGCLogParser::parseOutOfMemory));
        withoutGCIDRules.add(UnifiedGenZGCLogParser::parseZGCStatisticLine);

        withGCIDRules = new ArrayList<>(AbstractUnifiedGCLogParser.getSharedWithGCIDRules());
        // Young (Minor) generation phase rules
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Pause Mark Start", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Concurrent Mark", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Pause Mark End", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Concurrent Mark Free", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Concurrent Process Non-Strong References", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Concurrent Reset Relocation Set", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Concurrent Select Relocation Set", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Pause Relocate Start", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Young Concurrent Relocate", UnifiedGenZGCLogParser::parsePhase));
        
        // Major (Old) generation phase rules
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Pause Mark Start", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Concurrent Mark", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Pause Mark End", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Concurrent Mark Free", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Concurrent Process Non-Strong References", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Concurrent Reset Relocation Set", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Concurrent Select Relocation Set", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Pause Relocate Start", UnifiedGenZGCLogParser::parsePhase));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Concurrent Relocate", UnifiedGenZGCLogParser::parsePhase));
        
        // Common rules
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Metaspace", UnifiedGenZGCLogParser::parseMetaspace));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule(" Capacity", UnifiedGenZGCLogParser::parseHeap));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("     Used", UnifiedGenZGCLogParser::parseHeap));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Allocated", UnifiedGenZGCLogParser::parseHeap));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Reclaimed", UnifiedGenZGCLogParser::parseHeap));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Minor Collection", UnifiedGenZGCLogParser::parseMinorCollection));
        withGCIDRules.add(new ParseRule.PrefixAndValueParseRule("Major Collection", UnifiedGenZGCLogParser::parseMajorCollection));
    }

    @Override
    protected void doParseLineWithGCID(String detail, int gcid, double uptime) {
        ParseRule.ParseRuleContext context = new ParseRule.ParseRuleContext();
        context.put(UPTIME, uptime);
        context.put(GCID, gcid);
        doParseUsingRules(this, context, detail, withGCIDRules);
    }

    @Override
    protected void doParseLineWithoutGCID(String detail, double uptime) {
        ParseRule.ParseRuleContext context = new ParseRule.ParseRuleContext();
        context.put(UPTIME, uptime);
        doParseUsingRules(this, context, detail, withoutGCIDRules);
    }

    private static void parseMetaspace(AbstractGCLogParser parser, ParseRule.ParseRuleContext context, String prefix, String value) {
        GCModel model = parser.getModel();
        String[] parts = GCLogUtil.splitBySpace(value);
        GCEvent event = model.getLastEventOfType(GENZ_MINOR_COLLECTION);
        if (event == null) {
            event = model.getLastEventOfType(GENZ_MAJOR_COLLECTION);
        }
        if (event == null) {
            return;
        }
        String capacityString = parts.length == 6 ? parts[2] : parts[4];
        GCMemoryItem item = new GCMemoryItem(METASPACE, UNKNOWN_INT, UNKNOWN_INT,
                GCLogUtil.toByte(parts[0]), GCLogUtil.toByte(capacityString));
        event.setMemoryItem(item);
    }

    private static void parseOutOfMemory(AbstractGCLogParser parser, ParseRule.ParseRuleContext context, String prefix, String value) {
        GCModel model = parser.getModel();
        ThreadEvent event = new ThreadEvent();
        event.setThreadName(value.substring(1, value.length() - 1));
        event.setStartTime(context.get(UPTIME));
        model.addOom(event);
    }

    private static void parseHeap(AbstractGCLogParser parser, ParseRule.ParseRuleContext context, String prefix, String value) {
        GCModel model = parser.getModel();
        prefix = prefix.trim();
        String[] parts = GCLogUtil.splitBySpace(value);
        GCEvent event = model.getLastEventOfType(GENZ_MINOR_COLLECTION);
        if (event == null) {
            event = model.getLastEventOfType(GENZ_MAJOR_COLLECTION);
        }
        if (event == null) {
            return;
        }
        switch (prefix) {
            case "Capacity":
                GCMemoryItem item = new GCMemoryItem(HEAP);
                item.setPreCapacity(GCLogUtil.toByte(parts[0]));
                item.setPostCapacity(GCLogUtil.toByte(parts[6]));
                event.setMemoryItem(item);
                break;
            case "Used":
                item = event.getMemoryItem(HEAP);
                item.setPreUsed(GCLogUtil.toByte(parts[0]));
                item.setPostUsed(GCLogUtil.toByte(parts[6]));
                break;
            case "Reclaimed":
                event.setReclamation(GCLogUtil.toByte(parts[4]));
                break;
            case "Allocated":
                event.setAllocation(GCLogUtil.toByte(parts[5]));
                break;
        }
    }

    private static void parseMinorCollection(AbstractGCLogParser parser, ParseRule.ParseRuleContext context, String prefix, String value) {
        GCModel model = parser.getModel();
        int index = value.indexOf(')');
        GCEvent event;
        if (index == value.length() - 1) {
            // Start of minor collection: "Minor Collection (Warmup)"
            event = new GCEvent();
            model.putEvent(event);
            ((GenZGCModel) model).addMinorCollection(event);
            event.setStartTime(context.get(UPTIME));
            event.setEventType(GENZ_MINOR_COLLECTION);
            event.setCause(value.substring(1, index));
            event.setGcid(context.get(GCID));
        } else if (value.endsWith("%)")) {
            // End of minor collection: "Minor Collection (Warmup) 104M(10%)->88M(9%)"
            event = model.getLastEventOfType(GENZ_MINOR_COLLECTION);
            if (event == null) {
                return;
            }
            event.setDuration(context.<Double>get(UPTIME) - event.getStartTime());
        }
    }

    private static void parseMajorCollection(AbstractGCLogParser parser, ParseRule.ParseRuleContext context, String prefix, String value) {
        GCModel model = parser.getModel();
        int index = value.indexOf(')');
        GCEvent event;
        if (index == value.length() - 1) {
            // Start of major collection: "Major Collection (Warmup)"
            event = new GCEvent();
            model.putEvent(event);
            ((GenZGCModel) model).addMajorCollection(event);
            event.setStartTime(context.get(UPTIME));
            event.setEventType(GENZ_MAJOR_COLLECTION);
            event.setCause(value.substring(1, index));
            event.setGcid(context.get(GCID));
        } else if (value.endsWith("%)")) {
            // End of major collection: "Major Collection (Warmup) 155M(16%)->110M(11%)"
            event = model.getLastEventOfType(GENZ_MAJOR_COLLECTION);
            if (event == null) {
                return;
            }
            event.setDuration(context.<Double>get(UPTIME) - event.getStartTime());
        }
    }

    private static void parseAllocationStall(AbstractGCLogParser parser, ParseRule.ParseRuleContext context, String prefix, String value) {
        GCModel model = parser.getModel();
        String[] parts = GCLogUtil.splitByBracket(value);
        ThreadEvent event = new ThreadEvent();
        double endTime = context.get(UPTIME);
        double duration = GCLogUtil.toMillisecond(parts[1]);
        event.setStartTime(endTime - duration);
        event.setDuration(duration);
        event.setEventType(ZGC_ALLOCATION_STALL);
        event.setThreadName(parts[0]);
        ((GenZGCModel) model).addAllocationStalls(event);
    }

    private static void parsePhase(AbstractGCLogParser parser, ParseRule.ParseRuleContext context, String phaseName, String value) {
        GCModel model = parser.getModel();
        GCEventType eventType = getGCEventType(phaseName);
        GCEvent event = model.getLastEventOfType(eventType.getPhaseParentEventType());
        if (event == null) {
            // log may be incomplete
            return;
        }
        GCEvent phase = new GCEvent();
        double endTime = context.get(UPTIME);
        double duration = GCLogUtil.toMillisecond(value);
        phase.setGcid(event.getGcid());
        phase.setStartTime(endTime - duration);
        phase.setDuration(duration);
        phase.setEventType(eventType);
        model.addPhase(event, phase);
    }

    private static boolean parseZGCStatisticLine(AbstractGCLogParser parser, ParseRule.ParseRuleContext context, String text) {
        GenZGCModel model = (GenZGCModel) parser.getModel();
        String[] tokens = GCLogUtil.splitBySpace(text);
        int length = tokens.length;
        if (length >= 15 && "/".equals(tokens[length - 3]) && "/".equals(tokens[length - 6]) &&
                "/".equals(tokens[length - 9]) && "/".equals(tokens[length - 12])) {
            // make unit a part of type name to deduplicate
            String type = text.substring(0, text.indexOf('/') - 1 - tokens[length - 13].length()).trim()
                    + " " + tokens[length - 1];
            List<ZStatistics> statisticsList = model.getStatistics();
            ZStatistics statistics;
            if ("Collector: Garbage Collection Cycle ms".equals(type)) {
                statistics = new ZStatistics();
                statistics.setStartTime(context.get(UPTIME));
                statisticsList.add(statistics);
            } else if (statisticsList.isEmpty()) {
                // log is incomplete
                return true;
            } else {
                statistics = statisticsList.get(statisticsList.size() - 1);
            }
            ZStatisticsItem item = new ZStatisticsItem(
                    Double.parseDouble(tokens[length - 13]),
                    Double.parseDouble(tokens[length - 11]),
                    Double.parseDouble(tokens[length - 10]),
                    Double.parseDouble(tokens[length - 8]),
                    Double.parseDouble(tokens[length - 7]),
                    Double.parseDouble(tokens[length - 5]),
                    Double.parseDouble(tokens[length - 4]),
                    Double.parseDouble(tokens[length - 2]));
            statistics.put(type, item);
            return true;
        } else {
            return false;
        }
    }

    private static GCEventType getGCEventType(String eventString) {
        switch (eventString) {
            // Young/Minor phases
            case "Young Pause Mark Start":
                return GENZ_YOUNG_PAUSE_MARK_START;
            case "Young Concurrent Mark":
                return GENZ_YOUNG_CONCURRENT_MARK;
            case "Young Pause Mark End":
                return GENZ_YOUNG_PAUSE_MARK_END;
            case "Young Concurrent Mark Free":
                return GENZ_YOUNG_CONCURRENT_MARK_FREE;
            case "Young Concurrent Process Non-Strong References":
                return GENZ_YOUNG_CONCURRENT_NONREF;
            case "Young Concurrent Reset Relocation Set":
                return GENZ_YOUNG_CONCURRENT_RESET_RELOC_SET;
            case "Young Concurrent Select Relocation Set":
                return GENZ_YOUNG_CONCURRENT_SELECT_RELOC_SET;
            case "Young Pause Relocate Start":
                return GENZ_YOUNG_PAUSE_RELOCATE_START;
            case "Young Concurrent Relocate":
                return GENZ_YOUNG_CONCURRENT_RELOCATE;
            
            // Major/Old phases
            case "Major Pause Mark Start":
                return GENZ_MAJOR_PAUSE_MARK_START;
            case "Major Concurrent Mark":
                return GENZ_MAJOR_CONCURRENT_MARK;
            case "Major Pause Mark End":
                return GENZ_MAJOR_PAUSE_MARK_END;
            case "Major Concurrent Mark Free":
                return GENZ_MAJOR_CONCURRENT_MARK_FREE;
            case "Major Concurrent Process Non-Strong References":
                return GENZ_MAJOR_CONCURRENT_NONREF;
            case "Major Concurrent Reset Relocation Set":
                return GENZ_MAJOR_CONCURRENT_RESET_RELOC_SET;
            case "Major Concurrent Select Relocation Set":
                return GENZ_MAJOR_CONCURRENT_SELECT_RELOC_SET;
            case "Major Pause Relocate Start":
                return GENZ_MAJOR_PAUSE_RELOCATE_START;
            case "Major Concurrent Relocate":
                return GENZ_MAJOR_CONCURRENT_RELOCATE;
            
            default:
                throw new ShouldNotReachHereException();
        }
    }
}
