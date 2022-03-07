package org.odk.collect.android.smap.loaders;

import org.odk.collect.android.smap.loaders.PointEntry;
import org.odk.collect.android.smap.loaders.TaskEntry;

import java.util.ArrayList;

/**
 * Smap
 * This class holds the per-item data in ViewModel
 */
public class SurveyData {
    public ArrayList<TaskEntry> tasks;      // form or task
    public ArrayList<PointEntry> points;    // form or task
}
