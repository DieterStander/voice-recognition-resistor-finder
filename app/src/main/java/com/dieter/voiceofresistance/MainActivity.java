package com.dieter.voiceofresistance;
// TODO !? UI fine tuning (7 rules for UI)
// TODO ?? Continuous keyword detection
// TODO ?? Read back value or colors (TTS)

// TODO possible problems: permissions

import static android.speech.SpeechRecognizer.RESULTS_RECOGNITION;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    // Variable declarations
    // Number of bands on the resistor that will be set by the user. Default is 4.
    static int RES_BANDS = 4;
    static final int NOT_VALID_INPUT = 10;

    // Color value and multiplier constants for the resistor color code algorithm
    static final double BLACK_VALUE = 0;
    static final double BLACK_MULTIPLIER = Math.pow(10, BLACK_VALUE);
    static final double BROWN_VALUE = 1;
    static final double BROWN_MULTIPLIER = Math.pow(10, BROWN_VALUE);
    static final double RED_VALUE = 2;
    static final double RED_MULTIPLIER = Math.pow(10, RED_VALUE);
    static final double ORANGE_VALUE = 3;
    static final double ORANGE_MULTIPLIER = Math.pow(10, ORANGE_VALUE);
    static final double YELLOW_VALUE = 4;
    static final double YELLOW_MULTIPLIER = Math.pow(10, YELLOW_VALUE);
    static final double GREEN_VALUE = 5;
    static final double GREEN_MULTIPLIER = Math.pow(10, GREEN_VALUE);
    static final double BLUE_VALUE = 6;
    static final double BLUE_MULTIPLIER = Math.pow(10, BLUE_VALUE);
    static final double VIOLET_VALUE = 7;
    static final double VIOLET_MULTIPLIER = Math.pow(10, VIOLET_VALUE);
    static final double GRAY_VALUE = 8;
    static final double GRAY_MULTIPLIER = Math.pow(10, GRAY_VALUE);
    static final double WHITE_VALUE = 9;
    static final double WHITE_MULTIPLIER = Math.pow(10, WHITE_VALUE);
    static final double GOLD_MULTIPLIER = Math.pow(10, -1);
    static final double SILVER_MULTIPLIER = Math.pow(10, -2);

    // Patterns for checking valid inputs and standard values
    static final Pattern possibleColors = Pattern.compile("black|brown|red|orange|yellow|green|blue|violet|purple|gray|grey|white|gold|silver");
    static final Pattern possibleSuffixes = Pattern.compile("k|kilo|m|mega|ohm");
    static final Pattern possibleFloatValues = Pattern.compile("\\d*\\.\\d+");
    static final Pattern possibleIntegerValues = Pattern.compile("[0-9]+");

    ArrayList<LinearLayout> resistorBands = new ArrayList<>();
    ArrayList<LinearLayout> spacerBands = new ArrayList<>();
    ArrayList<ImageView> questionMarks = new ArrayList<>();
    Toast errorToast;
    TextView errorText;
    Animation animation;
    LinearLayout resistor;
    TextView valueText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize AdMob
        MobileAds.initialize(this, initializationStatus -> {});
        AdView mAdView;
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        linkUiContent();
        checkPermissions();

        if (savedInstanceState != null) {
            RES_BANDS = savedInstanceState.getInt("RES_BANDS");
            if (RES_BANDS == 4) {
                spacerBands.get(4).setVisibility(View.VISIBLE);
                spacerBands.get(5).setVisibility(View.GONE);

                resistorBands.get(3).setVisibility(View.VISIBLE);
                resistorBands.get(4).setVisibility(View.GONE);

                resistor.setBackgroundColor(ContextCompat.getColor(this, R.color.tan_resistor));
                for (LinearLayout spacerBand : spacerBands) {
                    spacerBand.setBackgroundColor(ContextCompat.getColor(this, R.color.tan_resistor));
                }
            } else {
                resistor.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_resistor));
                for (LinearLayout spacerBand : spacerBands) {
                    spacerBand.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_resistor));
                }

                spacerBands.get(4).setVisibility(View.VISIBLE);
                spacerBands.get(5).setVisibility(View.VISIBLE);

                resistorBands.get(3).setVisibility(View.VISIBLE);
                resistorBands.get(4).setVisibility(View.VISIBLE);
            }
            try {
                String valueToTest = savedInstanceState.getString("RESISTOR_VALUE").replace("Ω", "");
                if (!savedInstanceState.getString("RESISTOR_VALUE").equals("---")) {
                    findColorCodeFromValue(valueToTest.split(" "));
                }
            } catch (NullPointerException NP) {
                NP.printStackTrace();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("RESISTOR_VALUE", valueText.getText().toString());
        outState.putInt("RES_BANDS", RES_BANDS);
    }

    /**
     * Check for audio recording permission and display dialog if not granted access.
     */
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    /**
     * Callback to receive result of the permissions check. If denied, shows an error toast.
     *
     * @param requestCode   code of permission requested
     * @param permissions   permissions granted/refused
     * @param grantResults  permissions results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            errorText.setText(R.string.permissions_error);
            errorToast.show();
        }
    }

    /**
     * Link UI xml file to variables in code and initialise.
     */
    private void linkUiContent() {
        // Progress animation
        animation = new AlphaAnimation(1, 0);
        animation.setDuration(500);
        animation.setInterpolator(new LinearInterpolator());
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);

        valueText = findViewById(R.id.textView);
        valueText.setText("---");
        resistor = findViewById(R.id.resistor);

        createErrorToast();

        int[] spacerBandsIds = {R.id.spacer1, R.id.spacer2, R.id.spacer3, R.id.spacer4, R.id.spacer5, R.id.spacer6};
        int[] resistorBandViewIds = {R.id.firstBand, R.id.secondBand, R.id.thirdBand, R.id.fourthBand, R.id.fifthBand};
        int[] questionMarkIds = {R.id.question1, R.id.question2, R.id.question3, R.id.question4, R.id.question5};

        for (int spacerBandsId : spacerBandsIds) {
            spacerBands.add(findViewById(spacerBandsId));
        }
        for(int resistorBandViewId : resistorBandViewIds) {
            resistorBands.add(findViewById(resistorBandViewId));
        }
        for(int questionMarkId : questionMarkIds) {
            questionMarks.add(findViewById(questionMarkId));
        }

        resistorBands.get(4).setVisibility(View.GONE);
        spacerBands.get(5).setVisibility(View.GONE);
    }

    /**
     * Create and format error toast to be used in all error messages.
     */
    private void createErrorToast () {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast,
                findViewById(R.id.toast_layout_root));

        errorText = layout.findViewById(R.id.errorText);

        errorToast = new Toast(getApplicationContext());
        if(getResources().getConfiguration().orientation != 1) {
            errorToast.setGravity(Gravity.CENTER, 0, 0);
        } else {
            errorToast.setGravity(Gravity.CENTER, 0, 0);
        }

        errorToast.setDuration(Toast.LENGTH_LONG);
        errorToast.setView(layout);
    }

    /**
     * Hide or show question mark images on resistor.
     *
     * @param visible   boolean to specify images be hidden or not
     */
    private void setQuestionMarksVisibility(boolean visible) {
        if (visible) {
            for (ImageView questionMark : questionMarks) {
                questionMark.setVisibility(View.VISIBLE);
            }
        }else {
            for (ImageView questionMark : questionMarks) {
                questionMark.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Inflate the menu; this adds items to the action bar if it is present.
     *
     * @param menu  menu to be inflated
     * @return      true if successful
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Handles action bar item clicks.
     *
     * @param item  item clicked by the user
     * @return      true if handled successfully, false otherwise
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        for (LinearLayout resistorBand : resistorBands) {
            resistorBand.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.not_found));
        }

        if (id == R.id.action_4_band) {
            RES_BANDS = 4;
            setQuestionMarksVisibility(true);
            valueText.setText("---");
            resistor.setBackgroundColor(ContextCompat.getColor(this, R.color.tan_resistor));
            for (LinearLayout spacerBand : spacerBands) {
                spacerBand.setBackgroundColor(ContextCompat.getColor(this, R.color.tan_resistor));
            }

            spacerBands.get(4).setVisibility(View.VISIBLE);
            spacerBands.get(5).setVisibility(View.GONE);

            resistorBands.get(3).setVisibility(View.VISIBLE);
            resistorBands.get(4).setVisibility(View.GONE);

        } else if (id == R.id.action_5_band) {
            RES_BANDS = 5;
            setQuestionMarksVisibility(true);
            valueText.setText("---");
            resistor.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_resistor));
            for (LinearLayout spacerBand : spacerBands) {
                spacerBand.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_resistor));
            }

            spacerBands.get(4).setVisibility(View.VISIBLE);
            spacerBands.get(5).setVisibility(View.VISIBLE);

            resistorBands.get(3).setVisibility(View.VISIBLE);
            resistorBands.get(4).setVisibility(View.VISIBLE);
        }

        else if (id == R.id.action_information) {
            AlertDialog.Builder helpPage = new AlertDialog.Builder(this);
            helpPage.setTitle(R.string.action_information)
                    .setMessage(R.string.help_message)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {});

            AlertDialog helpPageDialog = helpPage.create();
            helpPageDialog.show();
        }

        String valueToTest = valueText.getText().toString().replace("Ω", "");
        if (!valueText.getText().toString().equals("---")) {
            findColorCodeFromValue(valueToTest.split(" "));
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Handle the voice search and results.
     *
     * @param view  button that called the onClick method
     */
    public void getVoiceSearchResults(View view){
        for (LinearLayout resistorBand : resistorBands) {
            resistorBand.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.not_found));
        }

        setQuestionMarksVisibility(true);
        valueText.setText("---");
        valueText.startAnimation(animation);
        if(SpeechRecognizer.isRecognitionAvailable(MainActivity.this)){
            RecognitionListener recognitionListener = new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    // Function is not required by the activity.
                }

                @Override
                public void onBeginningOfSpeech() {
                    // Function is not required by the activity.
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    //speakingBar.getLayoutParams().width = (int) rmsdB;
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Function is not required by the activity.
                }

                @Override
                public void onEndOfSpeech() {
                    // Function is not required by the activity.
                }

                @Override
                public void onError(int error) {
                    switch (error) {
                        case SpeechRecognizer.ERROR_AUDIO:
                            errorText.setText(R.string.audio_error);
                            errorToast.show();
                            break;

                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            errorText.setText(R.string.permissions_error);
                            errorToast.show();
                            break;

                        case SpeechRecognizer.ERROR_NETWORK:
                            errorText.setText(R.string.network_error);
                            errorToast.show();
                            break;

                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            errorText.setText(R.string.network_timeout_error);
                            errorToast.show();
                            break;

                        case SpeechRecognizer.ERROR_NO_MATCH:
                            errorText.setText(R.string.no_match_error);
                            errorToast.show();
                            break;

                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            errorText.setText(R.string.recogniser_busy_error);
                            errorToast.show();
                            break;

                        case SpeechRecognizer.ERROR_SERVER:
                            errorText.setText(R.string.server_error);
                            errorToast.show();
                            break;

                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            errorText.setText(R.string.speech_timeout_error);
                            errorToast.show();
                            break;

                        case NOT_VALID_INPUT:
                            errorText.setText(R.string.invalid_input);
                            errorToast.show();
                            break;

                        default:
                            errorText.setText(R.string.unknown_error);
                            errorToast.show();

                    }

                    for (LinearLayout resistorBand : resistorBands) {
                        resistorBand.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.not_found));
                    }
                    setQuestionMarksVisibility(true);
                    valueText.setText("---");
                    valueText.clearAnimation();
                }

                /** Process the results from the SpeechRecogniser. Test for valid strings and application path to take.
                 *
                 * @param results   results received from SpeechRecogniser
                 */
                @Override
                public void onResults(Bundle results) {
                    String[] searchResultArray = results.getStringArrayList(RESULTS_RECOGNITION).get(0).split(" ");
                    String[] lowerCaseResultArray = new String[searchResultArray.length];
                    valueText.clearAnimation();

                    for (int i = 0; i < searchResultArray.length; i++) {
                        lowerCaseResultArray[i] = searchResultArray[i].toLowerCase();
                    }

                    if (checkResultForColors(lowerCaseResultArray)) {
                        findValueFromColor(lowerCaseResultArray);
                    } else if (checkResultForValue(lowerCaseResultArray)) {
                        findColorCodeFromValue(lowerCaseResultArray);
                    } else {
                        onError(SpeechRecognizer.ERROR_AUDIO);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    // Function is not required by the activity.
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // Function is not required by the activity.
                }
            };
            Intent recogniserIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

            SpeechRecognizer speechRecognizer = SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
            speechRecognizer.setRecognitionListener(recognitionListener);
            speechRecognizer.startListening(recogniserIntent);
        } else {
            errorText.setText(R.string.search_not_installed);
            errorToast.show();

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://details?id=com.google.android.googlequicksearchbox"));
                startActivity(intent);
            } catch (ActivityNotFoundException AE) {
                AE.printStackTrace();
            }
        }
    }

    /**
     * Check if voice search result contains only the right number of valid colors. This indicates
     * that the user searched for a resistor color code.
     *
     * @param result    string array of voice search results
     * @return          true if string array contains only valid colors, false otherwise
     */
    private boolean checkResultForColors(String[] result) {
        if (result.length < RES_BANDS) return false;
        for (String aResult : result) {
            if (!possibleColors.matcher(aResult).matches()) return false;
        }
        return true;
    }

    /**
     * Check if voice search result contains numeric characters in first string and valid suffix in
     * second string. This indicates that the user searched for a resistor value.
     *
     * @param result    string array of voice search results
     * @return          true if string array contains only valid values and suffixes, false otherwise
     */
    private boolean checkResultForValue(String[] result) {
        if (result.length == 1) {
            return possibleIntegerValues.matcher(result[0]).matches() || possibleFloatValues.matcher(result[0]).matches();
        } else return ((possibleIntegerValues.matcher(result[0]).matches() || possibleFloatValues.matcher(result[0]).matches()) && possibleSuffixes.matcher(result[1]).matches());
    }

    /**
     * Checks the result for a tolerance specification.
     *
     * @param result    string array of voice search results
     * @return          tolerance string if present, empty string otherwise
     */
    private String checkResultForTolerance(String[] result) {
        for(String aResult : result) {
            if(aResult.contains("%")) {
                return aResult;
            }
        }
        return "";
    }

    /**
     * In case of an invalid input, sets valueText to the default and displays question marks on the resistor graphic.
     */
    private void invalidInput() {

        errorText.setText(R.string.invalid_input);
        errorToast.show();

        for (LinearLayout resistorBand : resistorBands) {
            resistorBand.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.not_found));
        }

        setQuestionMarksVisibility(true);
        valueText.setText("---");
    }

    /**
     * Finds the resistor value given a string array of valid colors.
     *
     * @param colorsToConvert   string array of voice search results checked for valid colors
     */
    private void findValueFromColor(String[] colorsToConvert){
        String[] lowerCaseColors = new String[colorsToConvert.length];
        StringBuilder resistanceBuilder = new StringBuilder(RES_BANDS);
        double resistanceCalculated = 0;
        for (int i = 0; i < colorsToConvert.length; i++) {
            lowerCaseColors[i] = colorsToConvert[i].toLowerCase();
        }

        setQuestionMarksVisibility(false);
        String toleranceValue = null;

        resistorBandsLoop:
        for (int i = 0; i < lowerCaseColors.length; i++) {
            switch (lowerCaseColors[i]) {
                case "black":
                    if (i == 0){
                        invalidInput();
                        return;
                    } else if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) BLACK_MULTIPLIER;
                    } else if (i == lowerCaseColors.length - 1) {
                        toleranceValue = " 20%";
                    } else resistanceBuilder.append("0");
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.black));
                    break;

                case "brown":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) BROWN_MULTIPLIER;
                    } else if (i == lowerCaseColors.length - 1) {
                        toleranceValue = " 1%";
                    } else {
                        resistanceBuilder.append("1");
                    }
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.brown));
                    break;

                case "red":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) RED_MULTIPLIER;
                    }else if (i == lowerCaseColors.length - 1) {
                        toleranceValue = " 2%";
                    } else {
                        resistanceBuilder.append("2");
                    }
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.red));
                    break;

                case "orange":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) ORANGE_MULTIPLIER;
                    } else resistanceBuilder.append("3");
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.orange));
                    break;

                case "yellow":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) YELLOW_MULTIPLIER;
                    }else resistanceBuilder.append("4");
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.yellow));
                    break;

                case "green":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) GREEN_MULTIPLIER;
                    } else resistanceBuilder.append("5");
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.green));
                    break;

                case "blue":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) BLUE_MULTIPLIER;
                    }else resistanceBuilder.append("6");
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.blue));
                    break;

                case "purple":
                case "violet":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) VIOLET_MULTIPLIER;
                    } else resistanceBuilder.append("7");
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.violet));
                    break;

                case "grey":
                case "gray":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) GRAY_MULTIPLIER;
                    } else resistanceBuilder.append("8");
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.gray));
                    break;

                case "white":
                    if (i == lowerCaseColors.length - 2) {
                        resistanceCalculated = Integer.parseInt(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * (int) WHITE_MULTIPLIER;
                    } else resistanceBuilder.append("9");
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.white));
                    break;

                case "gold":
                    if (i == lowerCaseColors.length - 1) {
                        toleranceValue = " 5%";
                    } else {
                        resistanceCalculated = Double.parseDouble(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * GOLD_MULTIPLIER;
                    }
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.gold_tolerance));
                    break;

                case "silver":
                    if (i == lowerCaseColors.length - 1) {
                        toleranceValue = " 10%";
                    } else {
                        resistanceCalculated = Double.parseDouble(resistanceBuilder.toString());
                        resistanceCalculated = resistanceCalculated * SILVER_MULTIPLIER;
                    }
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.silver_tolerance));
                    break;

                default:
                    invalidInput();
                    break resistorBandsLoop;
            }
        }

        if (toleranceValue == null) {
            toleranceValue = " 20%";
            resistorBands.get(RES_BANDS - 1).setBackgroundColor(ContextCompat.getColor(this, R.color.not_found));
            questionMarks.get(RES_BANDS - 1).setVisibility(View.VISIBLE);
            errorText.setText(R.string.invalid_tolerance_band);
            errorToast.show();
        }

        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        if (resistanceCalculated >= 1000 && resistanceCalculated < 1000000) {
            resistanceCalculated = resistanceCalculated/1000;
            valueText.setText(decimalFormat.format(resistanceCalculated) + " kΩ" + toleranceValue);
        } else if (resistanceCalculated >= 1000000) {
            resistanceCalculated = resistanceCalculated / 1000000;
            valueText.setText(decimalFormat.format(resistanceCalculated) + " MΩ" + toleranceValue);
        } else if (resistanceCalculated < 1000){
            valueText.setText(decimalFormat.format(resistanceCalculated) + " Ω" + toleranceValue);
        }
    }

    /**
     * Find the resistor color code from the value and suffix. Set valueText to the value spoken
     * by the user.
     *
     * @param valueToConvert    string array consisting of the value and suffix if present
     */
    private void findColorCodeFromValue(String[] valueToConvert) {
        String tolerance = checkResultForTolerance(valueToConvert);
        if (!tolerance.isEmpty()) {
            tolerance = " " + tolerance;
        } else {
            tolerance = " 20%"; // Default tolerance if no band is specified
        }

        String valueWithSuffix;
        if (valueToConvert.length > 1){
            switch (valueToConvert[1]) {
                case "kilo":
                case "k":
                    if (valueToConvert[0].matches("\\d+")){
                        valueWithSuffix = Integer.parseInt(valueToConvert[0]) + " kΩ" + tolerance;
                    } else valueWithSuffix = Double.parseDouble(valueToConvert[0]) + " kΩ" + tolerance;

                    valueText.setText(valueWithSuffix);
                    findColors(Double.parseDouble(valueToConvert[0]) * Math.pow(10, 3), tolerance);
                    break;

                case "mega":
                case "m":
                    if (valueToConvert[0].matches("\\d+")){
                        valueWithSuffix = Integer.parseInt(valueToConvert[0]) + " MΩ" + tolerance;
                    } else valueWithSuffix = Double.parseDouble(valueToConvert[0]) + " MΩ" + tolerance;

                    valueText.setText(valueWithSuffix);
                    findColors(Double.parseDouble(valueToConvert[0]) * Math.pow(10, 6), tolerance);
                    break;

                default:
                    if (valueToConvert[0].matches("\\d+")){
                        valueWithSuffix = Integer.parseInt(valueToConvert[0]) + " Ω" + tolerance;
                    } else valueWithSuffix = Double.parseDouble(valueToConvert[0]) + " Ω" + tolerance;

                    valueText.setText(valueWithSuffix);
                    findColors(Double.parseDouble(valueToConvert[0]), tolerance);
            }
        } else {
            if (valueToConvert[0].matches("\\d+")){
                valueWithSuffix = Integer.parseInt(valueToConvert[0]) + " Ω" + tolerance;
            } else {
                valueWithSuffix = Double.parseDouble(valueToConvert[0]) + " Ω" + tolerance;
            }
            valueText.setText(valueWithSuffix);
            findColors(Double.parseDouble(valueToConvert[0]), tolerance);
        }
    }

    /**
     * Find and set the color bands of the resistor.
     *
     * @param value     integer value of the resistor
     * @param tolerance tolerance string of the resistor
     */
    private void findColors(Double value, String tolerance){
        Character[] decimalValues = new Character[RES_BANDS - 1];
        String parsedValue;

        if (value < 10 && RES_BANDS == 4) {
            value = value * 10;
            parsedValue = Integer.toString(value.intValue());
            decimalValues[RES_BANDS - 2] = 'n';
            resistorBands.get(RES_BANDS - 2).setBackgroundColor(ContextCompat.getColor(this, R.color.gold_tolerance));
        } else if (value < 100 && RES_BANDS == 5) {
            if (value < 10) {
                value = value * 100;
                parsedValue = Integer.toString(value.intValue());
                decimalValues[RES_BANDS - 2] = 'n';
                resistorBands.get(RES_BANDS - 2).setBackgroundColor(ContextCompat.getColor(this, R.color.silver_tolerance));
            } else {
                value = value * 10;
                parsedValue = Integer.toString(value.intValue());
                decimalValues[RES_BANDS - 2] = 'n';
                resistorBands.get(RES_BANDS - 2).setBackgroundColor(ContextCompat.getColor(this, R.color.gold_tolerance));
            }
        } else {
            parsedValue = Integer.toString(value.intValue());
            decimalValues[RES_BANDS - 2] = Integer.toString(parsedValue.length() - (RES_BANDS - 2)).charAt(0);
        }

        for (int j = 0; j < RES_BANDS - 2; j++) {
            decimalValues[j] = parsedValue.charAt(j);
        }

        setQuestionMarksVisibility(false);

        for (int i = 0; i < decimalValues.length; i++) {
            switch (decimalValues[i]) {
                case '0':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.black));
                    break;

                case '1':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.brown));
                    break;

                case '2':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.red));
                    break;

                case '3':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.orange));
                    break;

                case '4':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.yellow));
                    break;

                case '5':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.green));
                    break;

                case '6':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.blue));
                    break;

                case '7':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.violet));
                    break;

                case '8':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.gray));
                    break;

                case '9':
                    resistorBands.get(i).setBackgroundColor(ContextCompat.getColor(this, R.color.white));
                    break;
            }
        }

        switch (tolerance) {
            case " 20%":
                resistorBands.get(RES_BANDS - 1).setBackgroundColor(ContextCompat.getColor(this, R.color.not_found));
                questionMarks.get(RES_BANDS - 1).setVisibility(View.VISIBLE);
                break;

            case " 10%":
                resistorBands.get(RES_BANDS - 1).setBackgroundColor(ContextCompat.getColor(this, R.color.silver_tolerance));
                break;

            case " 5%":
                resistorBands.get(RES_BANDS - 1).setBackgroundColor(ContextCompat.getColor(this, R.color.gold_tolerance));
                break;

            case " 2%":
                resistorBands.get(RES_BANDS - 1).setBackgroundColor(ContextCompat.getColor(this, R.color.red));
                break;

            case " 1%":
                resistorBands.get(RES_BANDS - 1).setBackgroundColor(ContextCompat.getColor(this, R.color.brown));
                break;
        }
    }
}
