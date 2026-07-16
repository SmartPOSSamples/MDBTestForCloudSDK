package com.cloudpos.mdbtestforjavadoc.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cloudpos.mdbtestforjavadoc.DataSendListener;
import com.cloudpos.mdbtestforjavadoc.R;

public class SniffsFragment extends Fragment implements View.OnClickListener{

	private Button 	btnClear,btnSniffsStart, btnSniffsStop;
	private Spinner spinnerFilter;
	private CheckBox checkBoxHidePoll;
	private Context context;

	private DataSendListener dataSendListener;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_sniffs, container, false);

//		btnGetVersion = (Button) view.findViewById(R.id.btn_get_version);
		btnSniffsStart = (Button) view.findViewById(R.id.btn_sniffs_start);
		btnSniffsStop = (Button) view.findViewById(R.id.btn_sniffs_stop);
//		btnGetMdbConnStatus = (Button) view.findViewById(R.id.btn_get_mdb_conn_status);
		spinnerFilter = view.findViewById(R.id.spinner_filter);
		checkBoxHidePoll = view.findViewById(R.id.cb_hide_poll);
		btnClear = view.findViewById(R.id.btn_clear);

//		btnGetVersion.setOnClickListener(this);
		btnSniffsStart.setOnClickListener(this);
		btnSniffsStop.setOnClickListener(this);
		btnClear.setOnClickListener(this);
//		btnGetMdbConnStatus.setOnClickListener(this);
		spinnerFilter.setSelection(0);
		spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				dataSendListener.onSelectFilter(position);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}
		});
		checkBoxHidePoll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				dataSendListener.onHidePoll(isChecked);
			}
		});

		context = getContext();

		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try{
			dataSendListener = (DataSendListener) context;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.btn_get_version:
				dataSendListener.onGetVersionClicked();
				break;
			case R.id.btn_sniffs_start:
				dataSendListener.onSniffsStartClicked();
				break;
			case R.id.btn_sniffs_stop:
				dataSendListener.onSniffsStopClicked();
				break;
			case R.id.btn_get_mdb_conn_status:
				dataSendListener.onGetMdbConnStatus();
				break;
			case R.id.btn_clear:
				dataSendListener.onClear();
				break;
		}
	}
}
