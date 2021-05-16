package com.vijay.maps;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiInterface {

    @GET("maps/api/directions/json")
    Single<Result> getDirectionFrom(@Query("mode") String mode,
                                @Query("transit_routing_preference") String preference,
                                @Query("origin") String origin,
                                @Query("destination") String destination,
                                @Query("key") String key);
}
