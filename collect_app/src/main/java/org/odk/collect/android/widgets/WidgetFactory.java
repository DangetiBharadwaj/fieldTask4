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

import android.app.Activity;
import android.content.Context;
import android.hardware.SensorManager;

import androidx.lifecycle.LifecycleOwner;

import org.javarosa.core.model.Constants;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.formentry.FormEntryViewModel;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.android.geo.MapProvider;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.utilities.Appearances;
import org.odk.collect.android.utilities.CameraUtils;
import org.odk.collect.android.utilities.CustomTabHelper;
import org.odk.collect.android.utilities.ExternalAppIntentProvider;
import org.odk.collect.android.utilities.MediaUtils;
import org.odk.collect.android.utilities.QuestionMediaManager;
import org.odk.collect.android.widgets.items.LabelWidget;
import org.odk.collect.android.widgets.items.LikertWidget;
import org.odk.collect.android.widgets.items.ListMultiWidget;
import org.odk.collect.android.widgets.items.ListWidget;
import org.odk.collect.android.widgets.items.RankingWidget;
import org.odk.collect.android.widgets.items.SelectMultiImageMapWidget;
import org.odk.collect.android.widgets.items.SelectMultiMinimalWidget;
import org.odk.collect.android.widgets.items.SelectMultiWidget;
import org.odk.collect.android.widgets.items.SelectOneImageMapWidget;
import org.odk.collect.android.widgets.items.SelectOneMinimalWidget;
import org.odk.collect.android.widgets.items.SelectOneWidget;
import org.odk.collect.android.widgets.utilities.ActivityGeoDataRequester;
import org.odk.collect.android.widgets.utilities.AudioPlayer;
import org.odk.collect.android.widgets.utilities.AudioRecorderRecordingStatusHandler;
import org.odk.collect.android.widgets.utilities.DateTimeWidgetUtils;
import org.odk.collect.android.widgets.utilities.GetContentAudioFileRequester;
import org.odk.collect.android.widgets.utilities.RecordingRequester;
import org.odk.collect.android.widgets.utilities.RecordingRequesterProvider;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;
import org.odk.collect.audiorecorder.recording.AudioRecorder;

import static org.odk.collect.android.utilities.Appearances.MAPS;
import static org.odk.collect.android.utilities.Appearances.PLACEMENT_MAP;
import static org.odk.collect.android.utilities.Appearances.hasAppearance;

/**
 * Convenience class that handles creation of widgets.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class WidgetFactory {

    private static final String PICKER_APPEARANCE = "picker";

    private final Activity context;
    private final boolean readOnlyOverride;
    private final boolean useExternalRecorder;
    private final WaitingForDataRegistry waitingForDataRegistry;
    private final QuestionMediaManager questionMediaManager;
    private final AudioPlayer audioPlayer;
    private final RecordingRequesterProvider recordingRequesterProvider;
    private final FormEntryViewModel formEntryViewModel;
    private final AudioRecorder audioRecorder;
    private final LifecycleOwner viewLifecycle;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public WidgetFactory(Activity activity,
                         boolean readOnlyOverride,
                         boolean useExternalRecorder,
                         WaitingForDataRegistry waitingForDataRegistry,
                         QuestionMediaManager questionMediaManager,
                         AudioPlayer audioPlayer,
                         RecordingRequesterProvider recordingRequesterProvider,
                         FormEntryViewModel formEntryViewModel,
                         AudioRecorder audioRecorder,
                         LifecycleOwner viewLifecycle) {
        this.context = activity;
        this.readOnlyOverride = readOnlyOverride;
        this.useExternalRecorder = useExternalRecorder;
        this.waitingForDataRegistry = waitingForDataRegistry;
        this.questionMediaManager = questionMediaManager;
        this.audioPlayer = audioPlayer;
        this.recordingRequesterProvider = recordingRequesterProvider;
        this.formEntryViewModel = formEntryViewModel;
        this.audioRecorder = audioRecorder;
        this.viewLifecycle = viewLifecycle;
    }

    public QuestionWidget createWidgetFromPrompt(FormEntryPrompt prompt, PermissionsProvider permissionsProvider) {
        String appearance = Appearances.getSanitizedAppearanceHint(prompt);
        QuestionDetails questionDetails = new QuestionDetails(prompt, Collect.getCurrentFormIdentifierHash(), readOnlyOverride);

        final QuestionWidget questionWidget;
        switch (prompt.getControlType()) {
            case Constants.CONTROL_INPUT:
                switch (prompt.getDataType()) {
                    case Constants.DATATYPE_DATE_TIME:
                        questionWidget = new DateTimeWidget(context, questionDetails, new DateTimeWidgetUtils());
                        break;
                    case Constants.DATATYPE_DATE:
                        questionWidget = new DateWidget(context, questionDetails, new DateTimeWidgetUtils());
                        break;
                    case Constants.DATATYPE_TIME:
                        questionWidget = new TimeWidget(context, questionDetails, new DateTimeWidgetUtils());
                        break;
                    case Constants.DATATYPE_DECIMAL:
                        if (appearance.startsWith(Appearances.EX)) {
                            questionWidget = new ExDecimalWidget(context, questionDetails, waitingForDataRegistry);
                        } else if (appearance.contains(Appearances.BEARING)) {      // smap change to contains rather than equals
                            questionWidget = new BearingWidget(context, questionDetails, waitingForDataRegistry,
                                    (SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
                        } else {
                            questionWidget = new DecimalWidget(context, questionDetails);
                        }
                        break;
                    case Constants.DATATYPE_INTEGER:
                        if (appearance.startsWith(Appearances.EX)) {
                            questionWidget = new ExIntegerWidget(context, questionDetails, waitingForDataRegistry);
                        } else {
                            questionWidget = new IntegerWidget(context, questionDetails);
                        }
                        break;
                    case Constants.DATATYPE_GEOPOINT:
                        if (hasAppearance(questionDetails.getPrompt(), PLACEMENT_MAP) || hasAppearance(questionDetails.getPrompt(), MAPS)) {
                            questionWidget = new GeoPointMapWidget(context, questionDetails, waitingForDataRegistry,
                                    new ActivityGeoDataRequester(permissionsProvider));
                        } else {
                            questionWidget = new GeoPointWidget(context, questionDetails, waitingForDataRegistry,
                                    new ActivityGeoDataRequester(permissionsProvider));
                        }
                        break;
                    case Constants.DATATYPE_GEOSHAPE:
                        questionWidget = new GeoShapeWidget(context, questionDetails, waitingForDataRegistry,
                                new ActivityGeoDataRequester(permissionsProvider));
                        break;
                    case Constants.DATATYPE_GEOTRACE:
                        questionWidget = new GeoTraceWidget(context, questionDetails, waitingForDataRegistry,
                                MapProvider.getConfigurator(), new ActivityGeoDataRequester(permissionsProvider));
                        break;
                    case Constants.DATATYPE_GEOCOMPOUND:    // smap
                        questionWidget = new GeoCompoundWidget(context, questionDetails, waitingForDataRegistry,
                                MapProvider.getConfigurator(), new ActivityGeoDataRequester(permissionsProvider));
                        break;
                    case Constants.DATATYPE_BARCODE:
                        if (appearance.contains("read_nfc")) {   // smap
                            questionWidget = new NfcWidget(context, questionDetails, waitingForDataRegistry);
                        } else {
                            questionWidget = new BarcodeWidget(context, questionDetails, waitingForDataRegistry, new CameraUtils());
                        }
                        break;
                    case Constants.DATATYPE_TEXT:
                        String query = prompt.getQuestion().getAdditionalAttribute(null, "query");
                        if (query != null) {
                            questionWidget = getSelectOneWidget(appearance, questionDetails);
                        } else if (appearance.contains(Appearances.PRINTER)) {   // smap change to contains rather than equals
                            questionWidget = new ExPrinterWidget(context, questionDetails, waitingForDataRegistry);
                        } else if (appearance.startsWith(Appearances.EX)) {
                            questionWidget = new ExStringWidget(context, questionDetails, waitingForDataRegistry);
                        } else if (appearance.contains(Appearances.NUMBERS)) {
                            questionWidget = new StringNumberWidget(context, questionDetails);
                        } else if (appearance.contains(Appearances.URL)) {	// smap change to contains rather than equals
                            questionWidget = new UrlWidget(context, questionDetails, new CustomTabHelper());
                        } else if (appearance.contains("chart")) {        // smap chart
                            String chartType = questionDetails.getPrompt().getQuestion().getAdditionalAttribute(null, "chart_type");
                            if(chartType == null) {
                                chartType = "line";
                            }
                            if(chartType.equals("line")) {
                                questionWidget = new SmapChartLineWidget(context, questionDetails, appearance);
                            } else if(chartType.equals("horizontal_bar")){
                                questionWidget = new SmapChartHorizontalBarWidget(context, questionDetails, appearance);
                            } else {
                                questionWidget = new SmapChartLineWidget(context, questionDetails, appearance);
                            }

                        } else if (questionDetails.getPrompt().getQuestion().getAdditionalAttribute(null, "form_identifier") != null) {	// smap
                            questionWidget = new SmapFormWidget(context, questionDetails, appearance, readOnlyOverride);	// smap
                        } else {
                            questionWidget = new StringWidget(context, questionDetails);
                        }
                        break;
                    default:
                        questionWidget = new StringWidget(context, questionDetails);
                        break;
                }
                break;
            case Constants.CONTROL_FILE_CAPTURE:
                if (appearance.startsWith(Appearances.EX)) {
                    questionWidget = new ExArbitraryFileWidget(context, questionDetails, new MediaUtils(), questionMediaManager, waitingForDataRegistry, new ExternalAppIntentProvider());
                } else {
                    questionWidget = new ArbitraryFileWidget(context, questionDetails, new MediaUtils(), questionMediaManager, waitingForDataRegistry);
                }
                break;
            case Constants.CONTROL_IMAGE_CHOOSE:
                if (appearance.contains(Appearances.SIGNATURE)) {    // smap change to contains rather than equals
                    questionWidget = new SignatureWidget(context, questionDetails, questionMediaManager, waitingForDataRegistry);
                } else if (appearance.contains(Appearances.ANNOTATE)) {
                    questionWidget = new AnnotateWidget(context, questionDetails, questionMediaManager, waitingForDataRegistry);
                } else if (appearance.contains(Appearances.DRAW)) {  // smap change to contains rather than equals
                    questionWidget = new DrawWidget(context, questionDetails, questionMediaManager, waitingForDataRegistry);
                } else if (appearance.startsWith(Appearances.EX)) {
                    questionWidget = new ExImageWidget(context, questionDetails, questionMediaManager, waitingForDataRegistry, new MediaUtils(), new ExternalAppIntentProvider());
                } else {
                    questionWidget = new ImageWidget(context, questionDetails, questionMediaManager, waitingForDataRegistry);
                }
                break;
            case Constants.CONTROL_OSM_CAPTURE:
                questionWidget = new OSMWidget(context, questionDetails, waitingForDataRegistry);
                break;
            case Constants.CONTROL_AUDIO_CAPTURE:
                RecordingRequester recordingRequester = recordingRequesterProvider.create(prompt, useExternalRecorder);
                GetContentAudioFileRequester audioFileRequester = new GetContentAudioFileRequester(context, waitingForDataRegistry, formEntryViewModel);

                if (appearance.startsWith(Appearances.EX)) {
                    questionWidget = new ExAudioWidget(context, questionDetails, questionMediaManager, audioPlayer, waitingForDataRegistry, new MediaUtils(), new ExternalAppIntentProvider());
                } else {
                    questionWidget = new AudioWidget(context, questionDetails, questionMediaManager, audioPlayer, recordingRequester, audioFileRequester, new AudioRecorderRecordingStatusHandler(audioRecorder, formEntryViewModel, viewLifecycle));
                }
                break;
            case Constants.CONTROL_VIDEO_CAPTURE:
                if (appearance.startsWith(Appearances.EX)) {
                    questionWidget = new ExVideoWidget(context, questionDetails, questionMediaManager, waitingForDataRegistry, new MediaUtils(), new ExternalAppIntentProvider());
                } else {
                    questionWidget = new VideoWidget(context, questionDetails, questionMediaManager, waitingForDataRegistry);
                }
                break;
            case Constants.CONTROL_SELECT_ONE:
                questionWidget = getSelectOneWidget(appearance, questionDetails);
                break;
            case Constants.CONTROL_SELECT_MULTI:
                // search() appearance/function (not part of XForms spec) added by SurveyCTO gets
                // considered in each widget by calls to ExternalDataUtil.getSearchXPathExpression.
                if (appearance.contains(Appearances.MINIMAL)) {
                    questionWidget = new SelectMultiMinimalWidget(context, questionDetails, waitingForDataRegistry);
                } else if (appearance.contains(Appearances.LIST_NO_LABEL)) {
                    questionWidget = new ListMultiWidget(context, questionDetails, false, readOnlyOverride);    //smap readOnlyOverride
                } else if (appearance.contains(Appearances.LIST)) {
                    questionWidget = new ListMultiWidget(context, questionDetails, true, readOnlyOverride);     //smap readOnlyOverride
                } else if (appearance.startsWith(Appearances.LABEL) || appearance.endsWith(Appearances.LABEL) || appearance.contains(" " + Appearances.LABEL + " ")) {  // smap ensure label is a standalone appearance
                    questionWidget = new LabelWidget(context, questionDetails);
                } else if (appearance.contains(Appearances.IMAGE_MAP)) {
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
                if (appearance.startsWith(Appearances.RATING)) {
                    questionWidget = new RatingWidget(context, questionDetails);
                } else {
                    switch (prompt.getDataType()) {
                        case Constants.DATATYPE_INTEGER:
                            if (prompt.getQuestion().getAppearanceAttr() != null && prompt.getQuestion().getAppearanceAttr().contains(PICKER_APPEARANCE)) {
                                questionWidget = new RangePickerIntegerWidget(context, questionDetails);
                            } else {
                                questionWidget = new RangeIntegerWidget(context, questionDetails);
                            }
                            break;
                        case Constants.DATATYPE_DECIMAL:
                            if (prompt.getQuestion().getAppearanceAttr() != null && prompt.getQuestion().getAppearanceAttr().contains(PICKER_APPEARANCE)) {
                                questionWidget = new RangePickerDecimalWidget(context, questionDetails);
                            } else {
                                questionWidget = new RangeDecimalWidget(context, questionDetails);
                            }
                            break;
                        default:
                            questionWidget = new StringWidget(context, questionDetails);
                            break;
                    }
                }
                break;
            default:
                questionWidget = new StringWidget(context, questionDetails);
                break;
        }

        return questionWidget;
    }

    private QuestionWidget getSelectOneWidget(String appearance, QuestionDetails questionDetails) {
        final QuestionWidget questionWidget;
        boolean isQuick = appearance.contains(Appearances.QUICK);
        // search() appearance/function (not part of XForms spec) added by SurveyCTO gets
        // considered in each widget by calls to ExternalDataUtil.getSearchXPathExpression.
        if (appearance.contains(Appearances.MINIMAL)) {
            questionWidget = new SelectOneMinimalWidget(context, questionDetails, isQuick, waitingForDataRegistry, readOnlyOverride);  // smap readOnlyOverride
        } else if (appearance.contains(Appearances.LIKERT)) {
            questionWidget = new LikertWidget(context, questionDetails);
        } else if (appearance.contains(Appearances.LIST_NO_LABEL)) {
            questionWidget = new ListWidget(context, questionDetails, false, isQuick, readOnlyOverride);   // smap readOnlyOverride
        } else if (appearance.contains(Appearances.LIST)) {
            questionWidget = new ListWidget(context, questionDetails, true, isQuick, readOnlyOverride);   // smap readOnlyOverride
        } else if (appearance.startsWith(Appearances.LABEL) || appearance.endsWith(Appearances.LABEL) || appearance.contains(" " + Appearances.LABEL + " ")) {  // smap ensure label is a standalone appearance
            questionWidget = new LabelWidget(context, questionDetails);
        } else if (appearance.contains(Appearances.IMAGE_MAP)) {
            questionWidget = new SelectOneImageMapWidget(context, questionDetails, isQuick);
        } else {
            questionWidget = new SelectOneWidget(context, questionDetails, isQuick, readOnlyOverride);   // smap
        }
        return questionWidget;
    }

}
