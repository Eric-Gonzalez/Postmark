package com.ericrgon.postmark;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.ericrgon.postmark.adapter.PagesAdapter;
import com.ericrgon.postmark.dialog.ShareLetterProgressDialog;
import com.ericrgon.postmark.dialog.StackDialog;
import com.ericrgon.postmark.model.Letter;
import com.ericrgon.postmark.model.Todo;
import com.ericrgon.postmark.rest.Callback;
import com.ericrgon.postmark.rest.OutboxMailService;
import com.ericrgon.postmark.task.ShareAsyncTask;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;

import retrofit.RetrofitError;
import retrofit.client.Response;

public class LetterDetailFragment extends Fragment {

    public static final String LETTER_ID = "letter";
    private static final String SHARE = "share";

    private Letter letter;

    private OutboxMailService mailService;

    private EventBus eventBus;

    private ShareAsyncTask shareAsyncTask;

    private Callback<Letter> letterActionCallback = new Callback<Letter>() {
        @Override
        public void success(Letter letter, Response response) {
            super.success(letter, response);
            eventBus.post(new LetterEvent());
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            super.failure(retrofitError);
            if(retrofitError.isNetworkError()){
                Toast.makeText(getActivity(),getString(R.string.network_issue),Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getActivity(),R.string.generic_error,Toast.LENGTH_LONG).show();
            }
        }
    };

    public static class LetterEvent {}
    public static class ShareEvent{
        private final ArrayList<Uri> images;

        public ShareEvent(ArrayList<Uri> images) {
            this.images = images;
        }

        public ArrayList<Uri> getImages() {
            return images;
        }
    }


    public LetterDetailFragment() {}

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mailService = ((BaseFragmentActivity) activity).getMailService();
        eventBus = ((BaseFragmentActivity) activity).getEventBus();
        eventBus.register(this);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(LETTER_ID)) {
            letter = (Letter) getArguments().getSerializable(LETTER_ID);
            getActivity().getActionBar().setTitle(letter.getDeliveredDate());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_share:
                shareLetter();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void shareLetter() {
        DialogFragment shareLetterProgressDialog = new ShareLetterProgressDialog();
        shareLetterProgressDialog.setRetainInstance(true);
        shareLetterProgressDialog.show(getFragmentManager(), SHARE);
        shareAsyncTask = new ShareAsyncTask(getActivity()){
            @Override
            protected void onPostExecute(ArrayList<Uri> cacheURIs) {
                super.onPostExecute(cacheURIs);
                eventBus.post(new ShareEvent(cacheURIs));
                }
        };

        shareAsyncTask.execute(letter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        RelativeLayout rootView = (RelativeLayout) inflater.inflate(R.layout.fragment_letter_detail, container, false);

        View shred = rootView.findViewById(R.id.shred);
        shred.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mailService.shred(letter.getIdentifier(), letterActionCallback);
            }
        });


        View move = rootView.findViewById(R.id.move);
        move.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StackDialog stackDialog = new StackDialog();
                stackDialog.setRetainInstance(true);
                stackDialog.show(getFragmentManager(), "stack");
            }
        });

        View todo = rootView.findViewById(R.id.todo);
        todo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mailService.todo(letter.getIdentifier(), System.currentTimeMillis(), Todo.EMPTY, letterActionCallback);
            }
        });

        View request = rootView.findViewById(R.id.request);
        request.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mailService.request(letter.getIdentifier(), System.currentTimeMillis(), letterActionCallback);
            }
        });

        //Hide the request button if the letter has be archived
        if (letter.isArchived()) {
            request.setVisibility(View.GONE);
        }

        final View actions = rootView.findViewById(R.id.actions);

        final ListView pagesList = (ListView) rootView.findViewById(R.id.pages_list);
        pagesList.setAdapter(new PagesAdapter(getActivity(), letter.getPages()));
        pagesList.setOnScrollListener(new AbsListView.OnScrollListener() {
            private int previous = 0;
            private int current = 0;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                int scrollY = getScrollY(pagesList);
                int height = actions.getHeight();
                int diff = scrollY - previous;
                current = clamp(current + diff, 0, height);
                actions.setTranslationY(current);
                previous = scrollY;
            }
        });

        return rootView;
    }

    @Subscribe
    public void onPageSelected(PagesAdapter.PageSelectedEvent pageSelectedEvent) {
        Intent pageZoomIntent = new Intent(getActivity(), PageZoomActivity.class);
        pageZoomIntent.putExtra(PageZoomActivity.PAGE, pageSelectedEvent.getPage());
        //Zero indexed
        pageZoomIntent.putExtra(PageZoomActivity.PAGE_NUMBER,pageSelectedEvent.getPageNumber() + 1);
        startActivity(pageZoomIntent);
    }

    @Subscribe
    public void onDimissEvent(ShareLetterProgressDialog.DismissedEvent dismissedEvent){
        //Kill the remaining downloads if the user dismissed the dialog.
        shareAsyncTask.cancel(true);
    }

    @Subscribe
    public void shareEvent(ShareEvent shareEvent){
        ArrayList<Uri> cacheURIs = shareEvent.getImages();
        DialogFragment stackDialog = (ShareLetterProgressDialog) getFragmentManager().findFragmentByTag(SHARE);
        if(stackDialog != null){
            stackDialog.dismiss();
        }

        //Build Share Intent.
        Intent shareLetter = new Intent();
        shareLetter.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareLetter.putParcelableArrayListExtra(Intent.EXTRA_STREAM,cacheURIs);
        shareLetter.setType("image/*");

        //Launch intent.
        startActivity(Intent.createChooser(shareLetter, getString(R.string.share_letter_to)));
    }

    public int getScrollY(ListView listView) {
        View c = listView.getChildAt(0);
        if (c == null) {
            return 0;
        }

        int firstVisiblePosition = listView.getFirstVisiblePosition();
        int top = c.getTop();

        int headerHeight = 0;
        if (firstVisiblePosition >= 1) {
            headerHeight = listView.getHeight();
        }

        return -top + firstVisiblePosition * c.getHeight() + headerHeight;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(Math.min(value, max), min);
    }

}
