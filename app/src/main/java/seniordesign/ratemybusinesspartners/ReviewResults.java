package seniordesign.ratemybusinesspartners;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBScanExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedQueryList;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedScanList;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.marshallers.CalendarToStringMarshaller;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;

import org.w3c.dom.Text;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import seniordesign.ratemybusinesspartners.adapters.ReviewListAdapter;
import seniordesign.ratemybusinesspartners.models.DummyDatabase;
import seniordesign.ratemybusinesspartners.models.Review;
import seniordesign.ratemybusinesspartners.models.User;

public class ReviewResults extends AppCompatActivity {

    // Strings to dictate queries
    public static final String SORT_BY_DATE_ASCENDING = "ReviewResults.Sort-Ascending";
    public static final String SORT_BY_DATE_DESCENDING = "ReviewResults.Sort-Date-Descending";
    public static final String SORT_BY_RATING_ASCENDING = "ReviewResults.Sort-Rating-Ascending";
    public static final String SORT_BY_RATING_DESCENDING = "ReviewResults.Sort-Rating-Descending";

    public static final String SHOW_ALL = "ReviewResults.Show-All";
    public static final String SHOW_LAST_WEEK = "ReviewResults.Show-Last-Week";
    public static final String SHOW_LAST_MONTH = "ReviewResults.Show-Last-Month";
    public static final String SHOW_LAST_3_MONTHS = "ReviewResults.Show-Last-3-Months";
    public static final String SHOW_LAST_6_MONTHS = "ReviewResults.Show-Last-6-Months";
    public static final String SHOW_LAST_12_MONTHS = "ReviewResults.Show-Last-12-Months";

    private String currentCompany;
    private User currentUser;

    private ArrayAdapter<CharSequence> sortBySpinnerAdapter;
    private ArrayAdapter<CharSequence> dateSpinnerAdapter;
    private ReviewListAdapter reviewResultsAdapter;

    // Ryan database variables
    AmazonDynamoDBClient ryanClient;
    DynamoDBMapper ryanMapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_results);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        currentCompany = intent.getStringExtra(CompanyProfile.COMPANY_PROFILE_TARGET_COMPANY);
        TextView companyName = (TextView) findViewById(R.id.reviewResultsCompanyName);
        companyName.setText(currentCompany);

        // Initialize Ryan Database
        initializeRyanDatabase();

        // Initialize Filters
        Spinner sortBySpinner = (Spinner) findViewById(R.id.sortBySpinner);
        sortBySpinnerAdapter = ArrayAdapter.createFromResource(this, R.array.sort_by_options, R.layout.support_simple_spinner_dropdown_item);
        sortBySpinner.setAdapter(sortBySpinnerAdapter);
        initializeSortBySpinnerListener(sortBySpinner);

        Spinner dateSpinner = (Spinner) findViewById(R.id.dateSpinner);
        dateSpinnerAdapter = ArrayAdapter.createFromResource(this, R.array.review_results_date_options, R.layout.support_simple_spinner_dropdown_item);
        dateSpinner.setAdapter(dateSpinnerAdapter);
        initializeDateSpinnerListener(dateSpinner);

        //Initialize Reviews
        ListView reviewResults = (ListView) findViewById(R.id.reviewResultsListView);
        reviewResultsAdapter = new ReviewListAdapter(this, new ArrayList<Review>());
        reviewResults.setAdapter(reviewResultsAdapter);

        AsyncListUpdate updater = new AsyncListUpdate();
        updater.execute(SORT_BY_DATE_DESCENDING, SHOW_LAST_6_MONTHS);

    }


    // Initialize Ryan's Database
    private void initializeRyanDatabase(){

        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "us-east-1:7af6d1e9-e1a2-45e5-8d91-8fb5be4b70d4", // Identity Pool ID
                Regions.US_EAST_1 // Region
        );

        this.ryanClient = new AmazonDynamoDBClient(credentialsProvider);
        this.ryanMapper = new DynamoDBMapper(ryanClient);

    }


    // OnItemSelected Listener methods

    private void initializeSortBySpinnerListener(Spinner spinner){

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d("Sort Spinner", "Position: " + position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }

    private void initializeDateSpinnerListener(Spinner spinner){

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d("Date Spinner", "Position: " + position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }


    /**
     * This Inner class is used to scan the review database and update the 'recent reviews' list
     * The scan is done in an AsyncTask and updates the ListView on the UI thread
     * UI operations have to be done on the onPreExecute() and onPostExecute() methods
     * The doInBackground() method is used to run the actual database scan
     */
    private class AsyncListUpdate extends AsyncTask<String, String, ArrayList<Review>> {

        @Override
        protected void onPreExecute(){

            // Clear the adapter
            reviewResultsAdapter.clear();

        }

        @Override
        protected ArrayList<Review> doInBackground(String... params) {


            Calendar relativeCalendar = Calendar.getInstance(); // Use this to filter based on result date
            CalendarToStringMarshaller calendarMarshaller = CalendarToStringMarshaller.instance();  // Marshaller to turn the Calendar into a String
            Map<String, AttributeValue> attributeValueMap = new HashMap<>();
            Map<String, String> attributeNameMap = new HashMap<>();
            DynamoDBScanExpression scanExpression;

            // Filter by date
            attributeNameMap.put("#D", "Date Created");
            switch (params[1]){

                case SHOW_ALL:
                    scanExpression = new DynamoDBScanExpression();
                    break;
                case SHOW_LAST_WEEK:
                    relativeCalendar.set(Calendar.WEEK_OF_MONTH, relativeCalendar.get(Calendar.WEEK_OF_MONTH) - 1);
                    attributeValueMap.put(":v1", calendarMarshaller.marshall(relativeCalendar));
                    scanExpression = new DynamoDBScanExpression().withFilterExpression("#D > :v1")
                            .withExpressionAttributeValues(attributeValueMap).withExpressionAttributeNames(attributeNameMap);
                    break;
                case SHOW_LAST_MONTH:
                    relativeCalendar.set(Calendar.MONTH, relativeCalendar.get(Calendar.MONTH) - 1);
                    attributeValueMap.put(":v1", calendarMarshaller.marshall(relativeCalendar));
                    scanExpression = new DynamoDBScanExpression().withFilterExpression("#D > :v1")
                            .withExpressionAttributeValues(attributeValueMap).withExpressionAttributeNames(attributeNameMap);
                    break;
                case SHOW_LAST_3_MONTHS:
                    relativeCalendar.set(Calendar.MONTH, relativeCalendar.get(Calendar.MONTH) - 3);
                    attributeValueMap.put(":v1", calendarMarshaller.marshall(relativeCalendar));
                    scanExpression = new DynamoDBScanExpression().withFilterExpression("#D > :v1")
                            .withExpressionAttributeValues(attributeValueMap).withExpressionAttributeNames(attributeNameMap);
                    break;
                case SHOW_LAST_6_MONTHS:
                    relativeCalendar.set(Calendar.MONTH, relativeCalendar.get(Calendar.MONTH) - 6);
                    attributeValueMap.put(":v1", calendarMarshaller.marshall(relativeCalendar));
                    scanExpression = new DynamoDBScanExpression().withFilterExpression("#D > :v1")
                            .withExpressionAttributeValues(attributeValueMap).withExpressionAttributeNames(attributeNameMap);
                    break;
                case SHOW_LAST_12_MONTHS:
                    relativeCalendar.set(Calendar.MONTH, relativeCalendar.get(Calendar.MONTH) - 12);
                    attributeValueMap.put(":v1", calendarMarshaller.marshall(relativeCalendar));
                    scanExpression = new DynamoDBScanExpression().withFilterExpression("#D > :v1")
                            .withExpressionAttributeValues(attributeValueMap).withExpressionAttributeNames(attributeNameMap);
                    break;
                default:
                    scanExpression = new DynamoDBScanExpression();
                    break;

            }

            PaginatedScanList<Review> scannedReviews = ryanMapper.scan(Review.class, scanExpression);

            ArrayList<Review> resultsList = new ArrayList<>();
            for(Review review : scannedReviews){
                resultsList.add(review);
            }

            return resultsList;
        }

        @Override
        protected void onPostExecute(ArrayList<Review> scanList){

            for(Review review : scanList){
                reviewResultsAdapter.add(review);
                Log.d("Review", review.getReviewText());
                Log.d("Date", review.getDateCreated().getTime().toString());
            }

        }
    }


}
