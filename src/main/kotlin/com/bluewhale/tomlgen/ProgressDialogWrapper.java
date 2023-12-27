package com.bluewhale.tomlgen;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ProgressDialogWrapper extends DialogWrapper {

    ProgressDialog progressDialog;

    public ProgressDialogWrapper()  {
        super(true); // use current window as parent
        init();
    }


    @Override
    protected @Nullable JComponent createCenterPanel() {
//        JPanel dialogPanel = new JPanel(new BorderLayout());

        progressDialog = new ProgressDialog();
        return progressDialog.getRootPane();
    }

    public void setProgressMsg(String msg) {
        progressDialog.setMsgLabelContent(msg);
    }
}
