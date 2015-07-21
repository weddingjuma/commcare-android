
package org.odk.collect.android.widgets;

import java.util.ArrayList;
import java.util.Vector;

import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryPrompt;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.Toast;

/**
 * AutoCompleteWidget handles select-one fields using an autocomplete text box. The user types part
 * of the desired selection and suggestions appear in a list below. The full list of possible
 * answers is not displayed to the user. The goal is to be more compact; this question type is best
 * suited for select one questions with a large number of possible answers. If images, audio, or
 * video are specified in the select answers they are ignored.
 * 
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class AutoCompleteWidget extends QuestionWidget {

    AutoCompleteAdapter choices;
    AutoCompleteTextView autocomplete;

    Vector<SelectChoice> mItems;

    // Defines which filter to use to display autocomplete possibilities
    @Nullable
    String filterType;

    // The various filter types
    @NonNull
    String match_substring = "substring";
    @NonNull
    String match_prefix = "prefix";
    @NonNull
    String match_chars = "chars";


    public AutoCompleteWidget(@NonNull Context context, @NonNull FormEntryPrompt prompt, @Nullable String filterType) {
        super(context, prompt);
        mItems = prompt.getSelectChoices();
        mPrompt = prompt;

        choices = new AutoCompleteAdapter(getContext(), android.R.layout.simple_list_item_1);
        autocomplete = new AutoCompleteTextView(getContext());

        // Default to matching substring
        if (filterType != null) {
            this.filterType = filterType;
        } else {
            this.filterType = match_substring;
        }

        for (SelectChoice sc : mItems) {
            choices.add(prompt.getSelectChoiceText(sc));
        }
        choices.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);

        autocomplete.setAdapter(choices);
        autocomplete.setTextColor(Color.BLACK);
        setGravity(Gravity.LEFT);

        // Fill in answer
        String s = null;
        if (mPrompt.getAnswerValue() != null) {
            s = ((Selection) mPrompt.getAnswerValue().getValue()).getValue();
        }

        for (int i = 0; i < mItems.size(); ++i) {
            String sMatch = mItems.get(i).getValue();

            if (sMatch.equals(s)) {
                autocomplete.setText(mItems.get(i).getLabelInnerText());
            }
        }

        addView(autocomplete);

    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#getAnswer()
     */
    @Nullable
    @Override
    public IAnswerData getAnswer() {
        String response = autocomplete.getText().toString();
        for (SelectChoice sc : mItems) {
            if (response.equals(mPrompt.getSelectChoiceText(sc))) {
                return new SelectOneData(new Selection(sc));
            }
        }

        // If the user has typed text into the autocomplete box that doesn't match any answer, warn
        // them that their
        // solution didn't count.
        if (!response.equals("")) {
            Toast.makeText(getContext(),
                "Warning: \"" + response + "\" does not match any answers. No answer recorded.",
                Toast.LENGTH_LONG).show();
        }
        return null;
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#clearAnswer()
     */
    @Override
    public void clearAnswer() {
        autocomplete.setText("");
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#setFocus(android.content.Context)
     */
    @Override
    public void setFocus(@NonNull Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
            (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);

    }

    private class AutoCompleteAdapter extends ArrayAdapter<String> implements Filterable {

        private ItemsFilter mFilter;
        public ArrayList<String> mItems;


        public AutoCompleteAdapter(@NonNull Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            mItems = new ArrayList<String>();
        }


        /*
         * (non-Javadoc)
         * @see android.widget.ArrayAdapter#add(java.lang.Object)
         */
        @Override
        public void add(String toAdd) {
            super.add(toAdd);
            mItems.add(toAdd);
        }


        /*
         * (non-Javadoc)
         * @see android.widget.ArrayAdapter#getCount()
         */
        @Override
        public int getCount() {
            return mItems.size();
        }


        /*
         * (non-Javadoc)
         * @see android.widget.ArrayAdapter#getItem(int)
         */
        @Override
        public String getItem(int position) {
            return mItems.get(position);
        }


        /*
         * (non-Javadoc)
         * @see android.widget.ArrayAdapter#getPosition(java.lang.Object)
         */
        @Override
        public int getPosition(String item) {
            return mItems.indexOf(item);
        }


        public Filter getFilter() {
            if (mFilter == null) {
                mFilter = new ItemsFilter(mItems);
            }
            return mFilter;
        }


        /*
         * (non-Javadoc)
         * @see android.widget.ArrayAdapter#getItemId(int)
         */
        @Override
        public long getItemId(int position) {
            return position;
        }

        private class ItemsFilter extends Filter {
            @NonNull
            final ArrayList<String> mItemsArray;


            public ItemsFilter(@Nullable ArrayList<String> list) {
                if (list == null) {
                    mItemsArray = new ArrayList<String>();
                } else {
                    mItemsArray = new ArrayList<String>(list);
                }
            }


            /*
             * (non-Javadoc)
             * @see android.widget.Filter#performFiltering(java.lang.CharSequence)
             */
            @NonNull
            @Override
            protected FilterResults performFiltering(@Nullable CharSequence prefix) {
                // Initiate our results object
                FilterResults results = new FilterResults();

                // If the adapter array is empty, check the actual items array and use it
                if (mItems == null) {
                    mItems = new ArrayList<String>(mItemsArray);
                }

                // No prefix is sent to filter by so we're going to send back the original array
                if (prefix == null || prefix.length() == 0) {
                    results.values = mItemsArray;
                    results.count = mItemsArray.size();
                } else {
                    // Compare lower case strings
                    String prefixString = prefix.toString().toLowerCase();

                    // Local to here so we're not changing actual array
                    final ArrayList<String> items = mItems;
                    final int count = items.size();
                    final ArrayList<String> newItems = new ArrayList<String>(count);

                    for (int i = 0; i < count; i++) {
                        final String item = items.get(i);
                        String item_compare = item.toLowerCase();

                        // Match the strings using the filter specified
                        if (filterType.equals(match_substring)
                                && (item_compare.startsWith(prefixString) || item_compare
                                        .contains(prefixString))) {
                            newItems.add(item);
                        } else if (filterType.equals(match_prefix)
                                && item_compare.startsWith(prefixString)) {
                            newItems.add(item);
                        } else if (filterType.equals(match_chars)) {
                            char[] toMatch = prefixString.toCharArray();

                            boolean matches = true;
                            for (int j = 0; j < toMatch.length; j++) {
                                int index = item_compare.indexOf(toMatch[j]);
                                if (index > -1) {
                                    item_compare =
                                        item_compare.substring(0, index)
                                                + item_compare.substring(index + 1);
                                } else {
                                    matches = false;
                                    break;
                                }
                            }

                            if (matches) {
                                newItems.add(item);
                            }

                        } else {
                            // Default to substring
                            if (item_compare.startsWith(prefixString)
                                    || item_compare.contains(prefixString)) {
                                newItems.add(item);
                            }
                        }
                    }

                    // Set and return
                    results.values = newItems;
                    results.count = newItems.size();
                }

                return results;
            }


            /*
             * (non-Javadoc)
             * @see android.widget.Filter#publishResults(java.lang.CharSequence, android.widget.Filter.FilterResults)
             */
            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, @NonNull FilterResults results) {
                mItems = (ArrayList<String>) results.values;
                // Let the adapter know about the updated list
                if (results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }

            }

        }

    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#setOnLongClickListener(android.view.View.OnLongClickListener)
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        autocomplete.setOnLongClickListener(l);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#cancelLongPress()
     */
    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        autocomplete.cancelLongPress();
    }

}
