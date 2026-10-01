package com.kankwj.angcode.connectors.shizuku;

import android.os.ParcelFileDescriptor;

interface IAngCodePrivilegedService {
    String run(in String[] command, int timeoutMs);
    String installApk(in ParcelFileDescriptor apk, long size, boolean replaceExisting);
    void destroy() = 16777114;
}
