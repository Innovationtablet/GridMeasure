#include <jni.h>
#include <string>
#include <vector>
#include "opencv2/aruco/charuco.hpp"
#include "opencv2/aruco/dictionary.hpp"
#include "opencv2/imgproc.hpp"

bool findCharuco(
        cv::Mat in,
        const cv::Ptr<cv::aruco::CharucoBoard> board,
        std::vector<cv::Point2f>& charucoCorners,
        std::vector<int>& charucoIds)
{
    std::vector< int > markerIds;
    std::vector< std::vector<cv::Point2f> > markerCorners;
    cv::aruco::detectMarkers(in, board->dictionary, markerCorners, markerIds);

    // if at least one marker detected
    if(markerIds.size() > 0) {
        cv::aruco::interpolateCornersCharuco(markerCorners, markerIds, in, board, charucoCorners, charucoIds);
        return true;
    }
    return false;
}


extern "C"
void Java_edu_psu_armstrong1_gridmeasure_GridDetectionUtils_calibrateWithCharuco(
    JNIEnv* env,
    jobject,
    jobjectArray imagePathsArray)
{

}

extern "C"
jstring
Java_edu_psu_armstrong1_gridmeasure_GridDetectionUtils_stringFromJNI(
        JNIEnv* env,
        jobject /* this */,
        jlong inMat,
        jlong outMat) {

    cv::Mat* pInMat = (cv::Mat*)inMat;
    cv::Mat* pOutMat = (cv::Mat*)outMat;

    cv::cvtColor(*pInMat, *pInMat, CV_RGB2GRAY);
    cv::resize(*pInMat, *pInMat, cv::Size(0,0), .1f, .1f, cv::INTER_NEAREST);


    // I'm getting errors if i try to draw the detected corners on the out mat if the out mat
    // is copied from in mat before in mat is turned to gray.
    *pOutMat = pInMat->clone();


    const cv::Ptr<cv::aruco::Dictionary> dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_7X7_1000);
    const cv::Ptr<cv::aruco::CharucoBoard> board = cv::aruco::CharucoBoard::create(5, 7, 100, 50, dictionary);

    std::vector<cv::Point2f> charucoCorners;
    std::vector<int> charucoIds;
    if (findCharuco(*pInMat, board, charucoCorners, charucoIds)) {
        cv::aruco::drawDetectedCornersCharuco(*pOutMat, charucoCorners, charucoIds, cv::Scalar(255,255,255));
    }

    std::string hello = "hello";
    return env->NewStringUTF(hello.c_str());
}
