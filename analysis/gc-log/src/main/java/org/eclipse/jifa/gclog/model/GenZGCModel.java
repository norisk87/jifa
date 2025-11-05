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

package org.eclipse.jifa.gclog.model;

import org.eclipse.jifa.gclog.event.GCEvent;
import org.eclipse.jifa.gclog.model.modeInfo.GCCollectorType;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.jifa.gclog.model.GCEventType.*;

/**
 * Model for Generational ZGC (introduced in JEP 474, Java 21+)
 * Generational ZGC has both young (minor) and old (major) generation collections
 */
public class GenZGCModel extends ZGCModel {

    private List<GCEvent> minorCollections = new ArrayList<>();
    private List<GCEvent> majorCollections = new ArrayList<>();

    private static GCCollectorType collector = GCCollectorType.GENZ;

    public GenZGCModel() {
        super(collector);
        this.setMetaspaceCapacityReliable(true);
    }

    private static List<GCEventType> allEventTypes = GCModel.calcAllEventTypes(collector);
    private static List<GCEventType> pauseEventTypes = GCModel.calcPauseEventTypes(collector);
    private static List<GCEventType> mainPauseEventTypes = GCModel.calcMainPauseEventTypes(collector);
    private static List<GCEventType> parentEventTypes = GCModel.calcParentEventTypes(collector);
    private static List<GCEventType> importantEventTypes = List.of(
            GENZ_MINOR_COLLECTION, GENZ_MAJOR_COLLECTION,
            GENZ_YOUNG_PAUSE_MARK_START, GENZ_YOUNG_PAUSE_MARK_END, GENZ_YOUNG_PAUSE_RELOCATE_START,
            GENZ_YOUNG_CONCURRENT_MARK, GENZ_YOUNG_CONCURRENT_NONREF, GENZ_YOUNG_CONCURRENT_RELOCATE,
            GENZ_MAJOR_PAUSE_MARK_START, GENZ_MAJOR_PAUSE_MARK_END, GENZ_MAJOR_PAUSE_RELOCATE_START,
            GENZ_MAJOR_CONCURRENT_MARK, GENZ_MAJOR_CONCURRENT_NONREF, GENZ_MAJOR_CONCURRENT_RELOCATE
    );

    @Override
    protected List<GCEventType> getAllEventTypes() {
        return allEventTypes;
    }

    @Override
    protected List<GCEventType> getPauseEventTypes() {
        return pauseEventTypes;
    }

    @Override
    protected List<GCEventType> getMainPauseEventTypes() {
        return mainPauseEventTypes;
    }

    @Override
    protected List<GCEventType> getImportantEventTypes() {
        return importantEventTypes;
    }

    @Override
    protected List<GCEventType> getParentEventTypes() {
        return parentEventTypes;
    }

    public List<GCEvent> getMinorCollections() {
        return minorCollections;
    }

    public void addMinorCollection(GCEvent minorCollection) {
        this.minorCollections.add(minorCollection);
    }

    public List<GCEvent> getMajorCollections() {
        return majorCollections;
    }

    public void addMajorCollection(GCEvent majorCollection) {
        this.majorCollections.add(majorCollection);
    }
}
