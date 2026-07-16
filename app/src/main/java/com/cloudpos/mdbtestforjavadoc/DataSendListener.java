package com.cloudpos.mdbtestforjavadoc;

import android.widget.CheckBox;

import java.math.BigDecimal;

public interface DataSendListener {
	void onLogSent(char c, String tag, String log);
	void onIntValueSent(int type, int value);
	void onStringValueSent(int type, String value);
	void onBooleanValueSent(int type, boolean value);
	void onBigDecimalValueSent(int type, BigDecimal value);

	void onOpenClicked();
	void onCloseClicked();
	void onGetVersionClicked();
	void onTestClicked();
	void onGetHardwareVersionClicked();
	void onDiagnoseHardwareClicked();
	void onMdbStartClicked();
	void onMdbStopClicked();
	void onSniffsStartClicked();
	void onSniffsStopClicked();

	void onTriggerPulseClicked();
	void onSetPulseClicked();
	void onFactoryModeClicked();
	void onGetMdbConnStatus();

//	void onSetBalanceClicked();

	void onTransactionFragmentViewCreated(CheckBox cb);

	void onSelectFilter(int position);

	void onHidePoll(boolean isChecked);

	void onClear();
}
