#include <jni.h>
#include <string>
#include <vector>
#include <iterator>
#include <fstream>
#include "opencv2/aruco/charuco.hpp"
#include "opencv2/aruco/dictionary.hpp"
#include "opencv2/imgproc.hpp"
#include "opencv2/highgui.hpp"
#include "opencv2/calib3d.hpp"

cv::Mat cameraMatrix, distCoeffs;
double repError;

std::string fileStoragePath;

bool calibrated = false;

// TODO Still not the best way to do this, but better.
// We should probably allow the charuco card to be configured on the Java end - but right now it's
// unimportant - we're just hard-coding the charuco card using this function.
// It may be overkill anyway to allow configuration of charuco card on the Java end - what practical
// use does that have?
const cv::Ptr<cv::aruco::CharucoBoard> getBoard() {
    const cv::Ptr<cv::aruco::Dictionary> dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_7X7_1000);
    return cv::aruco::CharucoBoard::create(5, 7, 1.0f, 0.5f, dictionary);
}

/**
 * http://stackoverflow.com/questions/41201641/write-a-vector-of-cvmat-to-binary-file-in-c
 */
void vecmatwrite(const std::string& filename, const std::vector<cv::Mat>& matrices)
{
    std::ofstream fs(filename, std::fstream::binary);

    for (size_t i = 0; i < matrices.size(); ++i)
    {
        const cv::Mat& mat = matrices[i];

        // Header
        int type = mat.type();
        int channels = mat.channels();
        fs.write((char*)&mat.rows, sizeof(int));    // rows
        fs.write((char*)&mat.cols, sizeof(int));    // cols
        fs.write((char*)&type, sizeof(int));        // type
        fs.write((char*)&channels, sizeof(int));    // channels

        // Data
        if (mat.isContinuous())
        {
            fs.write(mat.ptr<char>(0), (mat.dataend - mat.datastart));
        }
        else
        {
            int rowsz = CV_ELEM_SIZE(type) * mat.cols;
            for (int r = 0; r < mat.rows; ++r)
            {
                fs.write(mat.ptr<char>(r), rowsz);
            }
        }
    }
}

/**
 * http://stackoverflow.com/questions/41201641/write-a-vector-of-cvmat-to-binary-file-in-c
 */
std::vector<cv::Mat> vecmatread(const std::string& filename)
{
    std::vector<cv::Mat> matrices;
    std::ifstream fs(filename, std::fstream::binary);

    // Get length of file
    fs.seekg(0, fs.end);
    int length = fs.tellg();
    fs.seekg(0, fs.beg);

    while (fs.tellg() < length)
    {
        // Header
        int rows, cols, type, channels;
        fs.read((char*)&rows, sizeof(int));         // rows
        fs.read((char*)&cols, sizeof(int));         // cols
        fs.read((char*)&type, sizeof(int));         // type
        fs.read((char*)&channels, sizeof(int));     // channels

        // Data
        cv::Mat mat(rows, cols, type);
        fs.read((char*)mat.data, CV_ELEM_SIZE(type) * rows * cols);

        matrices.push_back(mat);
    }
    return matrices;
}

bool fileExists(std::string filename)
{
    std::ifstream file(filename);
    return file.good();
}

extern "C"
void Java_edu_psu_armstrong1_gridmeasure_GridDetectionUtils_init(
    JNIEnv* env,
    jobject,
    jstring fileStoragePathJstring)
{

    fileStoragePath = std::string(env->GetStringUTFChars(fileStoragePathJstring, JNI_FALSE)) + "/";

    if(fileExists(fileStoragePath + "calibration.xml")) {
        cv::FileStorage::FileStorage calibrationFile(fileStoragePath + "calibration.xml", cv::FileStorage::READ);
        calibrationFile["cameraMatrix"] >> cameraMatrix;
        calibrationFile["distCoeffs"] >> distCoeffs;
    }
}


extern "C"
void Java_edu_psu_armstrong1_gridmeasure_GridDetectionUtils_undistort(
        JNIEnv* env,
        jobject /* this */,
        jlong inMat,
        jlong outMat) {

    cv::Mat* pInMat = (cv::Mat*)inMat;
    cv::Mat* pOutMat = (cv::Mat*)outMat;

    cv::undistort(*pInMat, *pOutMat, cameraMatrix, distCoeffs);
}

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
    std::vector<cv::Mat> rvecs_unused, tvecs_unused;
    repError = cv::aruco::calibrateCameraCharuco(allCharucoCorners, allCharucoIds, board, imgSize, cameraMatrix, distCoeffs, rvecs_unused, tvecs_unused, calibrationFlags);

    // Save params.
    cv::FileStorage::FileStorage calibrationFile(fileStoragePath + "calibration.xml", cv::FileStorage::WRITE);
    calibrationFile << "cameraMatrix" << cameraMatrix << "distCoeffs" << distCoeffs;
    calibrationFile.release();

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

    const cv::Ptr<cv::aruco::CharucoBoard> board = getBoard();

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
    const cv::Ptr<cv::aruco::CharucoBoard> board = getBoard();

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
    const cv::Ptr<cv::aruco::CharucoBoard> board = getBoard();

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

    const cv::Ptr<cv::aruco::CharucoBoard> board = getBoard();

    std::vector<cv::Point2f> charucoCorners;
    std::vector<int> charucoIds;
    if (findCharuco(*pInMat, board, charucoCorners, charucoIds)) {
        cv::aruco::drawDetectedCornersCharuco(*pOutMat, charucoCorners, charucoIds, cv::Scalar(255,255,255));
    }

    std::string hello = "hello";
    return env->NewStringUTF(hello.c_str());
}
