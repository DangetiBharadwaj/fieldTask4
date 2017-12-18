/*
 * Copyright Smap Consulting 2017
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.Selection;
import android.text.method.TextKeyListener;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.odk.collect.android.R;
import org.odk.collect.android.external.ExternalDataUtil;
import org.odk.collect.android.external.ExternalSelectChoice;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.ViewIds;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 *
 *
 */
@SuppressLint("ViewConstructor")
public abstract class SmapChartWidget extends QuestionWidget {

    String appearance;
    boolean stacked = false;

    public SmapChartWidget(Context context, FormEntryPrompt prompt, String appearance) {
        super(context, prompt);
        this.appearance = appearance;
        String stackString = prompt.getQuestion().getAdditionalAttribute(null, "stacked");
        if(stackString != null && (stackString.equals("yes") || stackString.equals("true"))) {
            stacked = true;
        }
    }

    void addChart(Chart chart) {
        chart.setMinimumHeight(800);

        LinearLayout answerLayout = new LinearLayout(getContext());
        answerLayout.setOrientation(LinearLayout.VERTICAL);
        answerLayout.addView(chart);
        addAnswerView(answerLayout);
    }

    boolean isStacked() {
       return stacked;
    }

    @Override
    public void clearAnswer() {

    }


    @Override
    public IAnswerData getAnswer() {
        clearFocus();

        String s = getAnswerText();
        return !s.equals("") ? new StringData(s) : null;
    }

    @NonNull
    public String getAnswerText() {
        return "";
    }

    @Override
    public void setFocus(Context context) {

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return !event.isAltPressed() && super.onKeyDown(keyCode, event);
    }


    @Override
    public void setOnLongClickListener(OnLongClickListener l) {

    }


    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

    }

}