package edu.ucf.safemed;

import android.app.ProgressDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatDialogFragment;

public class DialogProcess extends AppCompatDialogFragment {
    private ProgressBar bar;
//    @Override
//    public Dialog onCreateDialog(Bundle savedInstanceState){
//        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//        builder.setTitle("Information")
//                .setMessage("This is a dialog")
//                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialogInterface, int i) {
//                    }
//                });
//        return builder.create();
//    }
@Override
public Dialog onCreateDialog(Bundle savedInstanceState) {
    ProgressDialog.Builder builder = new ProgressDialog.Builder(getActivity());

    LayoutInflater inflater = getActivity().getLayoutInflater();
    View view = inflater.inflate(R.layout.loading_dialog, null);

    builder.setView(view)
            .setTitle("");
//    bar = view.findViewById(R.id.progressBar);




    return builder.create();
}
}
