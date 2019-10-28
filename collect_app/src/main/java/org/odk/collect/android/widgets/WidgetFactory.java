/*
 * Copyright (C) 2009 University of Washington
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

import android.content.Context;

import org.javarosa.core.model.Constants;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.android.utilities.WidgetAppearanceUtils;

/**
 * Convenience class that handles creation of widgets.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class WidgetFactory {

    private WidgetFactory() {

    }

    /**
     * Returns the appropriate QuestionWidget for the given FormEntryPrompt.
     *
     * @param prompt              prompt element to be rendered
     * @param context          Android context
     * @param readOnlyOverride a flag to be ORed with JR readonly attribute.
     */
    public static QuestionWidget createWidgetFromPrompt(FormEntryPrompt prompt, Context context,
                                                        boolean readOnlyOverride) {

        String appearance = WidgetAppearanceUtils.getSanitizedAppearanceHint(prompt);
        QuestionDetails questionDetails = new QuestionDetails(prompt, Collect.getCurrentFormIdentifierHash());

        final QuestionWidget questionWidget;
        switch (prompt.getControlType()) {
            case Constants.CONTROL_INPUT:
                switch (prompt.getDataType()) {
                    case Constants.DATATYPE_DATE_TIME:
                        questionWidget = new DateTimeWidget(context, questionDetails);
                        break;
                    case Constants.DATATYPE_DATE:
                        questionWidget = new DateWidget(context, questionDetails);
                        break;
                    case Constants.DATATYPE_TIME:
                        questionWidget = new TimeWidget(context, questionDetails);
                        break;
                    case Constants.DATATYPE_DECIMAL:
                        if (appearance.startsWith(WidgetAppearanceUtils.EX)) {
                            questionWidget = new ExDecimalWidget(context, questionDetails);
                        } else if (appearance.contains(WidgetAppearanceUtils.BEARING)) {       // smap change to contains rather than equals
                            questionWidget = new BearingWidget(context, questionDetails);
                        } else {
                             questionWidget = new DecimalWidget(context, questionDetails, readOnlyOverride);
                        }
                        break;
                    case Constants.DATATYPE_INTEGER:
                        if (appearance.startsWith(WidgetAppearanceUtils.EX)) {
                            questionWidget = new ExIntegerWidget(context, questionDetails);
                        } else {
                            questionWidget = new IntegerWidget(context, questionDetails, readOnlyOverride);
                        }
                        break;
                    case Constants.DATATYPE_GEOPOINT:
                        questionWidget = new GeoPointWidget(context, questionDetails);
                        break;
                    case Constants.DATATYPE_GEOSHAPE:
                        questionWidget = new GeoShapeWidget(context, questionDetails);
                        break;
                    case Constants.DATATYPE_GEOTRACE:
                        questionWidget = new GeoTraceWidget(context, questionDetails);
                        break;
                    case Constants.DATATYPE_BARCODE:
                        if (appearance.contains("read_nfc")) {   // smap
                            questionWidget = new NfcWidget(context, fep);
                        } else {
                            questionWidget = new BarcodeWidget(context, questionDetails);
                        }
                        break;
                    case Constants.DATATYPE_TEXT:
                        String query = prompt.getQuestion().getAdditionalAttribute(null, "query");
                        if (query != null) {
                            questionWidget = new ItemsetWidget(context, questionDetails, appearance.startsWith(WidgetAppearanceUtils.QUICK), readOnlyOverride);   // smap add readOnlyOverride
                        } else if (appearance.contains(WidgetAppearanceUtils.PRINTER)) {    // smap change to contains rather than equals
                            questionWidget = new ExPrinterWidget(context, questionDetails);
                        } else if (appearance.startsWith(WidgetAppearanceUtils.EX)) {
                            questionWidget = new ExStringWidget(context, questionDetails);
                        } else if (appearance.contains(WidgetAppearanceUtils.NUMBERS)) {
                            questionWidget = new StringNumberWidget(context, questionDetails, readOnlyOverride);
                        } else if (appearance.contains(WidgetAppearanceUtils.URL)) {	// smap change to contains rather than equals
                            questionWidget = new UrlWidget(context, questionDetails);
                        } else if (appearance.contains("chart")) {        // smap chart
                            String chartType = fep.getQuestion().getAdditionalAttribute(null, "chart_type");
                            if(chartType == null) {
                                chartType = "line";
                            }
                            if(chartType.equals("line")) {
                                questionWidget = new SmapChartLineWidget(context, fep, appearance);
                            } else if(chartType.equals("horizontal_bar")){
                                questionWidget = new SmapChartHorizontalBarWidget(context, fep, appearance);
                            } else {
                                questionWidget = new SmapChartLineWidget(context, fep, appearance);
                            }

                        } else if (fep.getQuestion().getAdditionalAttribute(null, "form_identifier") != null) {	// smap
                            questionWidget = new SmapFormWidget(context, fep, appearance, readOnlyOverride);	// smap
                        } else {
                            questionWidget = new StringWidget(context, questionDetails, readOnlyOverride);
                        }
                        break;
                    case Constants.DATATYPE_BOOLEAN:
                        questionWidget = new BooleanWidget(context, questionDetails);
                        break;
                    default:
                        questionWidget = new StringWidget(context, questionDetails, readOnlyOverride);
                        break;
                }
                break;
            case Constants.CONTROL_FILE_CAPTURE:
                questionWidget = new ArbitraryFileWidget(context, questionDetails);
                break;
            case Constants.CONTROL_IMAGE_CHOOSE:
                if (appearance.contains(WidgetAppearanceUtils.SIGNATURE)) {	// smap change to contains rather than equals
                    questionWidget = new SignatureWidget(context, questionDetails);
                } else if (appearance.contains(WidgetAppearanceUtils.ANNOTATE)) {
                    questionWidget = new AnnotateWidget(context, questionDetails);
                } else if (appearance.contains(WidgetAppearanceUtils.DRAW)) {	// smap change to contains rather than equals
                    questionWidget = new DrawWidget(context, questionDetails);
                } else {
                    questionWidget = new ImageWidget(context, questionDetails);
                }
                break;
            case Constants.CONTROL_OSM_CAPTURE:
                questionWidget = new OSMWidget(context, questionDetails);
                break;
            case Constants.CONTROL_AUDIO_CAPTURE:
                questionWidget = new AudioWidget(context, questionDetails);
                break;
            case Constants.CONTROL_VIDEO_CAPTURE:
                questionWidget = new VideoWidget(context, questionDetails);
                break;
            case Constants.CONTROL_SELECT_ONE:
                // search() appearance/function (not part of XForms spec) added by SurveyCTO gets
                // considered in each widget by calls to ExternalDataUtil.getSearchXPathExpression.
                // This means normal appearances should be put before search().
                if (!appearance.contains(WidgetAppearanceUtils.COMPACT_N) && (appearance.contains(WidgetAppearanceUtils.COMPACT)   // smap contains
                        || appearance.contains(WidgetAppearanceUtils.QUICKCOMPACT)
                        || appearance.contains(WidgetAppearanceUtils.COLUMNS_PACK))) {
                    questionWidget = new GridWidget(context, questionDetails, appearance.contains(WidgetAppearanceUtils.QUICK));
                } else if (appearance.contains(WidgetAppearanceUtils.MINIMAL)) {
                    questionWidget = new SpinnerWidget(context, questionDetails, appearance.contains(WidgetAppearanceUtils.QUICK));
                } else if (appearance.contains(WidgetAppearanceUtils.SEARCH) || appearance.contains(WidgetAppearanceUtils.AUTOCOMPLETE)) {
                    questionWidget = new SelectOneSearchWidget(context, questionDetails, appearance.contains(WidgetAppearanceUtils.QUICK), readOnlyOverride);  // smap add readOnlyOverride
                } else if (appearance.contains(WidgetAppearanceUtils.LIST_NO_LABEL)) {
                    questionWidget = new ListWidget(context, questionDetails, false, appearance.contains(WidgetAppearanceUtils.QUICK));
                } else if (appearance.contains(WidgetAppearanceUtils.LIST)) {
                    questionWidget = new ListWidget(context, questionDetails, true, appearance.contains(WidgetAppearanceUtils.QUICK));
                } else if (appearance.equals(WidgetAppearanceUtils.LABEL)) {
                    questionWidget = new LabelWidget(context, questionDetails);
                } else if (appearance.contains(WidgetAppearanceUtils.IMAGE_MAP)) {
                    questionWidget = new SelectOneImageMapWidget(context, questionDetails, appearance.contains(WidgetAppearanceUtils.QUICK));
                } else {
                    questionWidget = new SelectOneWidget(context, questionDetails, appearance.contains(WidgetAppearanceUtils.QUICK), readOnlyOverride);  // smap
                }
                break;
            case Constants.CONTROL_SELECT_MULTI:
                // search() appearance/function (not part of XForms spec) added by SurveyCTO gets
                // considered in each widget by calls to ExternalDataUtil.getSearchXPathExpression.
                // This means normal appearances should be put before search().
                if (!appearance.startsWith(WidgetAppearanceUtils.COMPACT_N)
                        && (appearance.startsWith(WidgetAppearanceUtils.COMPACT)
                            || appearance.startsWith(WidgetAppearanceUtils.COLUMNS_PACK))) {
                    questionWidget = new GridMultiWidget(context, questionDetails);
                } else if (appearance.startsWith(WidgetAppearanceUtils.MINIMAL)) {
                    questionWidget = new SpinnerMultiWidget(context, questionDetails);
                } else if (appearance.startsWith(WidgetAppearanceUtils.LIST_NO_LABEL)) {
                    questionWidget = new ListMultiWidget(context, questionDetails, false);
                } else if (appearance.startsWith(WidgetAppearanceUtils.LIST)) {
                    questionWidget = new ListMultiWidget(context, questionDetails, true);
                } else if (appearance.startsWith(WidgetAppearanceUtils.LABEL)) {
                    questionWidget = new LabelWidget(context, questionDetails);
                } else if (appearance.contains(WidgetAppearanceUtils.SEARCH) || appearance.contains(WidgetAppearanceUtils.AUTOCOMPLETE)) {
                    questionWidget = new SelectMultipleAutocompleteWidget(context, questionDetails, readOnlyOverride);  // smap
                } else if (appearance.startsWith(WidgetAppearanceUtils.IMAGE_MAP)) {
                    questionWidget = new SelectMultiImageMapWidget(context, questionDetails);
                } else {
                    questionWidget = new SelectMultiWidget(context, questionDetails, readOnlyOverride);  // smap
                }
                break;
            case Constants.CONTROL_RANK:
                questionWidget = new RankingWidget(context, questionDetails);
                break;
            case Constants.CONTROL_TRIGGER:
                questionWidget = new TriggerWidget(context, questionDetails);
                break;
            case Constants.CONTROL_RANGE:
                if (appearance.startsWith(WidgetAppearanceUtils.RATING)) {
                    questionWidget = new RatingWidget(context, questionDetails);
                } else {
                    switch (prompt.getDataType()) {
                        case Constants.DATATYPE_INTEGER:
                            questionWidget = new RangeIntegerWidget(context, questionDetails);
                            break;
                        case Constants.DATATYPE_DECIMAL:
                            questionWidget = new RangeDecimalWidget(context, questionDetails);
                            break;
                        default:
                            questionWidget = new StringWidget(context, questionDetails, readOnlyOverride);
                            break;
                    }
                }
                break;
            default:
                questionWidget = new StringWidget(context, questionDetails, readOnlyOverride);
                break;
        }

        return questionWidget;
    }
}
