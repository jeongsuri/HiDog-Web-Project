window.addEventListener("DOMContentLoaded", function() {
    // 1. 지도 띄우기
    let map = new Tmapv2.Map("map", {
        center : new Tmapv2.LatLng(37.56520450, 126.98702028),
        width : "100%",
        height : "400px",
        zoom : 17,
        zoomControl : true,
        scrollwheel : true
    });


    // 2. 시작, 도착 심볼찍기
    // 시작
    marker_s = new Tmapv2.Marker(
        {
            position : new Tmapv2.LatLng(37.564991,126.983937),
            /*icon : "/upload/tmap/marker/pin_r_m_s.png",
            iconSize : new Tmapv2.Size(24, 38), */
            map : map
        });

    // 도착
    marker_e = new Tmapv2.Marker(
        {
            position : new Tmapv2.LatLng(37.566158,126.988940),
            /* icon : "/upload/tmap/marker/pin_r_m_e.png",
            iconSize : new Tmapv2.Size(24, 38), */
            map : map
        });


    const data = {
        "startX" : "126.983937",
        "startY" : "37.564991",
        "endX" : "126.988940",
        "endY" : "37.566158",
        "reqCoordType" : "WGS84GEO",
        "resCoordType" : "EPSG3857",
        "startName" : "출발지",
        "endName" : "도착지"
    };


    const { ajaxLoad } = commonLib;
    let resultdrawArr = drawInfoArr = [];

    (async() => {
        const headers = { appKey: "AMJpAtQzfg6RGV9RB1oHM3ZaCPp0T0Qx4nYhDbCZ"};
        const url = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&format=json&callback=result";
        try {
            const response = await ajaxLoad(url, "POST", data, headers, "JSON");

            const resultData = response.features;

            drawInfoArr = [];

            for ( var i in resultData) { //for문 [S]
                var geometry = resultData[i].geometry;
                var properties = resultData[i].properties;
                var polyline_;


                if (geometry.type == "LineString") {
                    for ( var j in geometry.coordinates) {
                        // 경로들의 결과값(구간)들을 포인트 객체로 변환
                        var latlng = new Tmapv2.Point(
                            geometry.coordinates[j][0],
                            geometry.coordinates[j][1]);
                        // 포인트 객체를 받아 좌표값으로 변환
                        var convertPoint = new Tmapv2.Projection.convertEPSG3857ToWGS84GEO(
                            latlng);
                        // 포인트객체의 정보로 좌표값 변환 객체로 저장
                        var convertChange = new Tmapv2.LatLng(
                            convertPoint._lat,
                            convertPoint._lng);
                        // 배열에 담기
                        drawInfoArr.push(convertChange);
                    }
                } else {
                    var markerImg = "";
                    var pType = "";
                    var size;

                    if (properties.pointType == "S") { //출발지 마커
                        // markerImg = "/upload/tmap/marker/pin_r_m_s.png";
                        pType = "S";
                        // size = new Tmapv2.Size(24, 38);
                    } else if (properties.pointType == "E") { //도착지 마커
                        // markerImg = "/upload/tmap/marker/pin_r_m_e.png";
                        pType = "E";
                        // size = new Tmapv2.Size(24, 38);
                    } else { //각 포인트 마커
                        // markerImg = "http://topopen.tmap.co.kr/imgs/point.png";
                        pType = "P";
                        // size = new Tmapv2.Size(8, 8);
                    }

                    // 경로들의 결과값들을 포인트 객체로 변환
                    var latlon = new Tmapv2.Point(
                        geometry.coordinates[0],
                        geometry.coordinates[1]);

                    // 포인트 객체를 받아 좌표값으로 다시 변환
                    var convertPoint = new Tmapv2.Projection.convertEPSG3857ToWGS84GEO(
                        latlon);

                    var routeInfoObj = {
                        // markerImage : markerImg,
                        lng : convertPoint._lng,
                        lat : convertPoint._lat,
                        pointType : pType
                    };

                    // Marker 추가
                    marker_p = new Tmapv2.Marker(
                        {
                            position : new Tmapv2.LatLng(
                                routeInfoObj.lat,
                                routeInfoObj.lng),
                            // icon : routeInfoObj.markerImage,
                            // iconSize : size,
                            map : map
                        });
                }
            }//for문 [E]
            drawLine(drawInfoArr);

            console.log(response);
        } catch (err) {
            console.log(err);
        }
    })();

    function addComma(num) {
        var regexp = /\B(?=(\d{3})+(?!\d))/g;
        return num.toString().replace(regexp, ',');
    }

    function drawLine(arrPoint) {
        var polyline_;

        polyline_ = new Tmapv2.Polyline({
            path : arrPoint,
            strokeColor : "#DD0000",
            strokeWeight : 6,
            map : map
        });
        resultdrawArr.push(polyline_);
    }

});

