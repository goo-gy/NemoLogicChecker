package com.example.nemologicchecker.ui.home;

import static java.lang.Integer.parseInt;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.nemologicchecker.R;
import com.example.nemologicchecker.databinding.FragmentHomeBinding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HomeFragment extends Fragment {
    // --------------------------------------------------------
    private static final int REQUEST_ENABLE_BT = 10; // 블루투스 활성화 상태
    private BluetoothAdapter bluetoothAdapter; // 블루투스 어댑터
    private Set<BluetoothDevice> set_device; // 블루투스 디바이스 데이터 셋
    private BluetoothDevice bluetoothDevice; // 블루투스 디바이스
    private BluetoothSocket bluetoothSocket = null; // 블루투스 소켓
    private InputStream inputStream = null; // 블루투스에 데이터를 입력하기 위한 입력 스트림
    private Thread workerThread = null; // 문자열 수신에 사용되는 쓰레드
    private byte[] readBuffer; // 수신 된 문자열을 저장하기 위한 버퍼
    private int readBufferPosition; // 버퍼 내 문자 저장 위치
    private TextView textViewReceive; // 수신 된 데이터를 표시하기 위한 텍스트 뷰

    private OutputStream outputStream = null; // 블루투스에 데이터를 출력하기 위한 출력 스트림
    private EditText editTextSend; // 송신 할 데이터를 작성하기 위한 에딧 텍스트
    private Button buttonSend; // 송신하기 위한 버튼

    ProgressBar progressBar;
    int progressData = 40;

    List<String> list_device = new ArrayList<>();
    ArrayAdapter<String> arrayAdapterDevice;// = new ArrayAdapter<String>(getContext(), android.R.layout.select_dialog_singlechoice);
    // --------------------------------------------------------

    private HomeViewModel homeViewModel;
    private FragmentHomeBinding binding;


    public void initializeSetting()
    {

    }

    public void setProgress(int data)
    {
        progressData = data;
        textViewReceive.setText(progressData + "%");
        progressBar.setProgress(progressData);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        View root = binding.getRoot();

        textViewReceive = binding.textHome;

        progressBar = (ProgressBar)binding.proBar;
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(progressData);

        setProgress(40);
        // --- func
//        View btn_throw_dice = binding.btnDice;
//        View bluetooth_list = binding.listviewScore;
        // ----------------------------------------------------- bluetooth
        arrayAdapterDevice = new ArrayAdapter<String>( getContext(), android.R.layout.select_dialog_singlechoice);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); // Bluetooth 어댑터
        if (bluetoothAdapter == null) {
            // TODO: handle (블루투스 장치가 없을 때)
        } else {
            if (bluetoothAdapter.isEnabled()) // Bluetooth 활성화 (기기에 블루투스가 켜져있음)
            {
                findDevices();
            } else // Bluetooth 비활성화
            {
                // 블루투스 활성화 Dialog
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                // Callback in 'onActivityResult'
                startActivityForResult(intent, REQUEST_ENABLE_BT);
            }
        }
        // ----------------------------------------------------- !bluetooth
        // ----------------------------------------------------- Popup
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());
        alertBuilder.setTitle("BluetoothDevice");

        // 버튼 생성
        alertBuilder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        // Adapter 셋팅
        alertBuilder.setAdapter(arrayAdapterDevice, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                // AlertDialog 안에 있는 AlertDialog
                String deviceName = arrayAdapterDevice.getItem(id);
                connectDevice(deviceName);

                // NOTE: ha
                AlertDialog.Builder innBuilder = new AlertDialog.Builder(getContext());
                innBuilder.setMessage(deviceName);
                innBuilder.setTitle("당신이 선택한 것은 ");
                innBuilder.setPositiveButton("확인", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                innBuilder.show();
            }
        });
        alertBuilder.show();
        //--------------
        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            Log.d("ONAct", "Checked");
            // TODO: Bluetooth 켜졌으니 Device 선택할 수 있게
            findDevices();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void findDevices() {
        set_device = bluetoothAdapter.getBondedDevices(); // 페어링된 Devices
        bluetoothAdapter.startDiscovery();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        getContext().registerReceiver(receiver, filter);

        int pairedDeviceCount = set_device.size();

        if (pairedDeviceCount > 0) {
            // 디바이스를 선택하기 위한 다이얼로그 생성
            for (BluetoothDevice bluetoothDevice : set_device) {
                String deviceName = bluetoothDevice.getName();
                arrayAdapterDevice.add(deviceName);
                Log.d("getName", deviceName);
            }
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();

                // TODO: Make btArray
                Toast.makeText(getContext(), "Find: " + deviceName, Toast.LENGTH_LONG).show();
                arrayAdapterDevice.add(deviceName);
                arrayAdapterDevice.notifyDataSetChanged();

                // String deviceHardwareAddress = device.getAddress(); // MAC
                // deviceAddressArray.add(deviceHardwareAddress);
            }
        }
    };

    public void connectDevice(String deviceName) {
        // 페어링 된 디바이스들을 모두 탐색
        for (BluetoothDevice tempDevice : set_device) {
            // 사용자가 선택한 이름과 같은 디바이스로 설정하고 반복문 종료
            if (deviceName.equals(tempDevice.getName())) {
                bluetoothDevice = tempDevice;
                break;
            }
        }

        // UUID 생성
        UUID uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        // Rfcomm 채널을 통해 블루투스 디바이스와 통신하는 소켓 생성
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            // 데이터 송,수신 스트림을 얻어옵니다.
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            Toast.makeText(getContext(), deviceName + " 연결 성공!", Toast.LENGTH_LONG).show();
            // 데이터 수신 함수 호출
            receiveData();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Bluetooth 연결 실패!", Toast.LENGTH_LONG).show();
        }
    }

    public void receiveData() {
        final Handler handler = new Handler();
        // 데이터를 수신하기 위한 버퍼를 생성
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        // 데이터를 수신하기 위한 쓰레드 생성

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("DataReceive", "start");
//                while (Thread.currentThread().isInterrupted()) {
                while (true) {
                    try {
                        // Check whether receive
                        int byteAvailable = inputStream.available();
                        // if receive
                        if(byteAvailable > 0) {
                            Log.d("DataReceive", Integer.toString(byteAvailable));
                            // 입력 스트림에서 바이트 단위로 읽어 옵니다.

                            byte[] bytes = new byte[byteAvailable];

                            inputStream.read(bytes);

                            // 입력 스트림 바이트를 한 바이트씩 읽어 옵니다.

                            for(int i = 0; i < byteAvailable; i++) {
                                byte tempByte = bytes[i];
                                // 개행문자를 기준으로 받음(한줄)
                                if(tempByte == '\n') {
                                    // readBuffer 배열을 encodedBytes로 복사
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    // 인코딩 된 바이트 배열을 문자열로 변환
                                    final String msg = new String(encodedBytes, "US-ASCII");
                                    Log.d("DataReceive", msg);
                                    readBufferPosition = 0;
                                    try {
                                        int parseData = parseInt(msg);
                                        setProgress(parseData);
                                    } catch (NumberFormatException e) {
                                        Log.d("DataReceive", "error:" + msg);
                                    }
                                } // 개행 문자가 아닐 경우

                                else {

                                    readBuffer[readBufferPosition++] = tempByte;

                                }

                            }

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        // 1초마다 받아옴
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        workerThread.start();
    }
}