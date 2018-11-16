package com.github.axet.filemanager;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.axet.androidlibrary.app.Storage;

public class FilesFragment extends Fragment {
    private static final String ARG_SECTION_NUMBER = "section_number";

    public static final int RESULT_PERMS = 1;

    public FilesFragment() {
    }

    public static FilesFragment newInstance(int sectionNumber) {
        FilesFragment fragment = new FilesFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        View refresh = rootView.findViewById(R.id.action_refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        return rootView;
    }

    public void refresh() {
        if (Storage.permitted(this, Storage.PERMISSIONS_RW, RESULT_PERMS))
            ;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_PERMS:
                break;
        }
    }
}
