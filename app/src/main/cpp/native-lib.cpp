#include <jni.h>
#include <string>
#include <vector>
#include <iterator>
#include "opencv2/aruco/charuco.hpp"
#include "opencv2/aruco/dictionary.hpp"
#include "opencv2/imgproc.hpp"
#include "opencv2/highgui.hpp"
#include "opencv2/calib3d.hpp"

cv::Mat cameraMatrix, distCoeffs;
std::vector<cv::Mat> rvecs, tvecs;
double repError;

bool calibrated = false;

/**
 *
 */
cv::Point2f imagePointToWorldPoint(
        cv::Point2f imagePoint,
        cv::Mat rvec,
        cv::Mat tvec)
{
    cv::Mat rotationMatrix;
    cv::Rodrigues(rvec,rotationMatrix);


    cv::Mat uvPoint = cv::Mat::ones(3,1,cv::DataType<double>::type); //u,v,1
    uvPoint.at<double>(0,0) = (double)imagePoint.x; //got this point using mouse callback
    uvPoint.at<double>(1,0) = (double)imagePoint.y;

    cv::Mat tempMat, tempMat2;
    double s;
    tempMat = rotationMatrix.inv() * cameraMatrix.inv() * uvPoint;
    tempMat2 = rotationMatrix.inv() * tvec;
    s = 0 + tempMat2.at<double>(2,0); // we can set z to whatever; here we set it to 0.
    s /= tempMat.at<double>(2,0);

    cv::Mat point =  rotationMatrix.inv() * (s * cameraMatrix.inv() * uvPoint - tvec);
    return cv::Point2f(point.at<double>(0,0), point.at<double>(1,0));
}

/**
 * Image must be grayscale.
 */
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

/**
 * Images must be grayscale
 */
bool calibrateWithCharuco(
        cv::Ptr<cv::aruco::CharucoBoard> board,
        const std::vector<cv::Mat>& images,
        cv::Size imgSize)
{
    std::vector< std::vector<cv::Point2f> > allCharucoCorners;
    std::vector< std::vector<int> > allCharucoIds;

    for (cv::Mat image : images)
    {
        std::vector<cv::Point2f> charucoCorners;
        std::vector<int> charucoIds;

        if (findCharuco(image, board, charucoCorners, charucoIds))
        {
            allCharucoCorners.push_back(charucoCorners);
            allCharucoIds.push_back(charucoIds);
        }
    }


    int calibrationFlags = 0;
    repError = cv::aruco::calibrateCameraCharuco(allCharucoCorners, allCharucoIds, board, imgSize, cameraMatrix, distCoeffs, rvecs, tvecs, calibrationFlags);

    // Mark as calibrated
    calibrated = true;

    return true;
}

/**
 *  outlinePointsJfloatArray will be an array of floats. Take them two at a time to get the x,y pairs.
 *  Clearly, this means outlinePointsJfloatArray should be of even length.
 */
extern "C"
jfloatArray Java_edu_psu_armstrong1_gridmeasure_GridDetectionUtils_measurementsFromOutlineNative(
    JNIEnv* env,
    jobject,
    jobject imageJobject,
    jfloatArray outlinePointsJfloatArray)
{
    if (!calibrated)
    {
        cameraMatrix = cv::Mat::eye(3, 3, CV_64F);
        distCoeffs = cv::Mat::zeros(8, 1, CV_64F);  // todo is this right?
    }

    jfloatArray err = env->NewFloatArray(0);

    jclass matclass = env->FindClass("org/opencv/core/Mat");
    jmethodID getPtrMethod = env->GetMethodID(matclass, "getNativeObjAddr", "()J");

    cv::Mat image = *(cv::Mat*)env->CallLongMethod(imageJobject, getPtrMethod);

    // TODO this shouldn't go here
    const cv::Ptr<cv::aruco::Dictionary> dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_7X7_1000);
    const cv::Ptr<cv::aruco::CharucoBoard> board = cv::aruco::CharucoBoard::create(5, 7, 100, 50, dictionary);

    //estimatePoseCharucoBoard
    std::vector<cv::Point2f> charucoCorners;
    std::vector<int> charucoIds;

    // todo handle error
    if (!findCharuco(image, board, charucoCorners, charucoIds)) return err;

    cv::Mat rvec,tvec;
    // todo handle error
    if (!cv::aruco::estimatePoseCharucoBoard(charucoCorners, charucoIds, board, cameraMatrix, distCoeffs, rvec, tvec)) return err;

    // Get the float array.
    jfloat* jfloatArr = env->GetFloatArrayElements(outlinePointsJfloatArray, 0);

    float* outPoints = new float[env->GetArrayLength(outlinePointsJfloatArray)];
    // Note that we jump in increments of two.
    // Todo - should we check that the length of the array is even?
    for (int i = 0; i < env->GetArrayLength(outlinePointsJfloatArray); i += 2)
    {
        cv::Point2f worldPoint = imagePointToWorldPoint(cv::Point2f(cv::Point2f(jfloatArr[i], jfloatArr[i+1])), rvec, tvec);
        outPoints[i] = worldPoint.x;
        outPoints[i+1] = worldPoint.y;
    }

    jfloatArray out = env->NewFloatArray(env->GetArrayLength(outlinePointsJfloatArray));
    env->SetFloatArrayRegion(out, 0, env->GetArrayLength(outlinePointsJfloatArray), outPoints);

    delete [] outPoints;

    return out;
}

extern "C"
void Java_edu_psu_armstrong1_gridmeasure_GridDetectionUtils_calibrateWithCharucoNative(
    JNIEnv* env,
    jobject,
    jobjectArray imagePathsArray)
{
    // TODO this shouldn't go here
    const cv::Ptr<cv::aruco::Dictionary> dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_7X7_1000);
    const cv::Ptr<cv::aruco::CharucoBoard> board = cv::aruco::CharucoBoard::create(5, 7, 100, 50, dictionary);

    std::vector<cv::Mat> images;

    cv::Size imgSize;

    for (int i = 0; i < env->GetArrayLength(imagePathsArray); i++)
    {
        // Get path
        jstring pathJstring = (jstring) (env->GetObjectArrayElement(imagePathsArray, i));
        const char *path = env->GetStringUTFChars(pathJstring, 0);

        cv::Mat image = cv::imread(path, CV_LOAD_IMAGE_GRAYSCALE);
        if (image.data != NULL)
        {
            images.push_back(image);
            if (imgSize.height == 0 && imgSize.width == 0) imgSize = image.size();
            else {
                // TODO we should handle an error here.
            }
        }
    }

    calibrateWithCharuco(board, images, imgSize);
}

extern "C"
void Java_edu_psu_armstrong1_gridmeasure_GridDetectionUtils_calibrateWithCharucoMatsNative(
    JNIEnv* env,
    jobject,
    jobjectArray imageArray)
{
    // TODO this shouldn't go here
    const cv::Ptr<cv::aruco::Dictionary> dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_7X7_1000);
    const cv::Ptr<cv::aruco::CharucoBoard> board = cv::aruco::CharucoBoard::create(5, 7, 100, 50, dictionary);

    std::vector<cv::Mat> images;

    jclass matclass = env->FindClass("org/opencv/core/Mat");
    jmethodID getPtrMethod = env->GetMethodID(matclass, "getNativeObjAddr", "()J");

    cv::Size imgSize;

    for (int i = 0; i < env->GetArrayLength(imageArray); i++)
    {
        cv::Mat image = *(cv::Mat*)env->CallLongMethod(env->GetObjectArrayElement(imageArray, i), getPtrMethod);

        if (image.data != NULL)
        {
            images.push_back(image);
            if (imgSize.height == 0 && imgSize.width == 0) imgSize = image.size();
            else {
                // TODO we should handle an error here.
            }
        }
    }

    calibrateWithCharuco(board, images, imgSize);
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
