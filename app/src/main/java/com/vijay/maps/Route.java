package com.vijay.maps;

public class Route {

    private OverviewPolyline overview_polyline;

    public Route(OverviewPolyline overviewPolyline) {
        this.overview_polyline = overviewPolyline;
    }

    public OverviewPolyline getOverview_polyline() {
        return overview_polyline;
    }

    public void setOverview_polyline(OverviewPolyline overview_polyline) {
        this.overview_polyline = overview_polyline;
    }
}
