package com.kankwj.angcode.connectors.shizuku;

import android.os.ParcelFileDescriptor;

interface IAngCodePrivilegedService {
    String run(in String[] command, int timeoutMs) = 1;
    String installApk(in ParcelFileDescriptor apk, long size, boolean replaceExisting) = 2;
    String captureScreen(in ParcelFileDescriptor output) = 3;
    void destroy() = 16777114;
}
