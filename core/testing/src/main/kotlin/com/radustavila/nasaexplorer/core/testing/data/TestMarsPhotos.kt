package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.core.model.MarsRover
import java.time.Instant

object TestMarsPhotos {

    private const val PERSEVERANCE_IMAGES = "https://mars.nasa.gov/mars2020-raw-images/pub/ods/surface/sol"

    fun curiosity(
        id: String = "5048MH0009060011804545C00_DXXX",
        sol: Int = 5048,
        cameraId: String = "MAHLI",
        cameraName: String = "Mars Hand Lens Imager (MAHLI)",
        takenAt: Instant? = Instant.parse("2026-10-18T20:22:12Z"),
    ): MarsPhoto = MarsPhoto(
        id = id,
        rover = MarsRover.CURIOSITY,
        sol = sol,
        cameraId = cameraId,
        cameraName = cameraName,
        takenAt = takenAt,
        imageUrl = "https://mars.nasa.gov/msl-raw-images/msss/0$sol/mhli/$id.jpg",
        description = "NASA's Mars rover Curiosity acquired this image using its Mars Hand Lens Imager.",
        credit = "NASA/JPL-Caltech/MSSS",
    )

    fun perseverance(
        id: String = "NLF_1650_0812345678_123ECM_N0123456NCAM00500_01_295J",
        sol: Int = 1650,
        cameraId: String = "NAVCAM_LEFT",
        cameraName: String = "Left Navigation Camera (Navcam)",
        takenAt: Instant? = Instant.parse("2026-10-17T21:05:12Z"),
    ): MarsPhoto = MarsPhoto(
        id = id,
        rover = MarsRover.PERSEVERANCE,
        sol = sol,
        cameraId = cameraId,
        cameraName = cameraName,
        takenAt = takenAt,
        imageUrl = "$PERSEVERANCE_IMAGES/0$sol/ids/edr/browse/ncam/${id}_1200.jpg",
        thumbnailUrl = "$PERSEVERANCE_IMAGES/0$sol/ids/edr/browse/ncam/${id}_320.jpg",
        description = "NASA's Mars Perseverance rover acquired this image using its Left Navigation Camera.",
        credit = "NASA/JPL-Caltech",
    )
}
