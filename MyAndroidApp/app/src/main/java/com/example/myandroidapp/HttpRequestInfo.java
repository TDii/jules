package com.example.myandroidapp;

import android.os.Parcel;
import android.os.Parcelable;

public class HttpRequestInfo implements Parcelable {
    public String method;
    public String uri;
    public String version;
    public String host;
    // Add other fields as needed, e.g., sourcePort, destPort

    public HttpRequestInfo(String method, String uri, String version, String host) {
        this.method = method;
        this.uri = uri;
        this.version = version;
        this.host = host;
    }

    @Override
    public String toString() {
        return method + " " + (host != null ? host : "") + uri + " (" + version + ")";
    }

    // Parcelable implementation
    protected HttpRequestInfo(Parcel in) {
        method = in.readString();
        uri = in.readString();
        version = in.readString();
        host = in.readString();
    }

    public static final Creator<HttpRequestInfo> CREATOR = new Creator<HttpRequestInfo>() {
        @Override
        public HttpRequestInfo createFromParcel(Parcel in) {
            return new HttpRequestInfo(in);
        }

        @Override
        public HttpRequestInfo[] newArray(int size) {
            return new HttpRequestInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(method);
        dest.writeString(uri);
        dest.writeString(version);
        dest.writeString(host);
    }
}
