package com.example.viewfuli;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xiaojf on 18/1/8.
 */

public class PhotoResult {
    private boolean error;
    private List<PhotoItem> results = new ArrayList<>();


    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public List<PhotoItem> getResults() {
        return results;
    }

    public void setResults(List<PhotoItem> results) {
        this.results = results;
    }
}
