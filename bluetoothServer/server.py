"""Python Bluetooth Server for Byte Data

Accepts incoming bluetooth connections and can save all data to files.
All messages have a 4 byte header containing a number representing the number of 
    bytes in the message EXCLUDING the header. The message contents follow the header.
    
Code derived from a tutorial posted by David Vassallo on his blog:
    http://blog.davidvassallo.me/
    http://blog.davidvassallo.me/2014/05/11/android-linux-raspberry-pi-bluetooth-communication/
    
In order to use Bluetooth, you must install pybluez.
"""

import os
import glob
import time
import logging
import os.path
import time
from bluetooth import *
from binascii import hexlify
from binascii import unhexlify



#Constants
MESSAGE_SIZE_BYTES = 4
STANDARD_RECEIVE_SIZE = 1024
UUID = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

#Globals
logFile = 'server.log'
saveToDir = './incomingData'
logLevel = logging.INFO
saveFiles = True

#base_dir = '/sys/bus/w1/devices/'
#device_folder = glob.glob(base_dir + '28*')[0]
#device_file = device_folder + '/w1_slave'



def runServer (directory, isSaving):
    # Make sure logging is enabled
    if 'logger' not in globals():
        setupLogging(logLevel)


    logger.info("Starting server...")
    
    if isSaving:
        logger.info("Saving all incoming data to %s" % directory)
    else:
        logger.info("Not saving any incoming data")
    
    # Setup server socket
    server_sock=BluetoothSocket( RFCOMM )
    server_sock.bind(("",PORT_ANY))
    server_sock.listen(1)

    port = server_sock.getsockname()[1]


    advertise_service( server_sock, "ArmstrongCutter",
                       service_id = UUID,
                       service_classes = [ UUID, SERIAL_PORT_CLASS ],
                       profiles = [ SERIAL_PORT_PROFILE ], 
    #                   protocols = [ OBEX_UUID ] 
                        )
                        
    # Server loop
    while True:          
        logger.info("Waiting for connection on RFCOMM channel %s" % port)

        # Wait for an incoming connection
        client_sock, client_info = server_sock.accept()
        logger.info("Accepted connection from %s" % str(client_info))

        try:
            # Get the message size
            msgSizeEncoded = client_sock.recv(MESSAGE_SIZE_BYTES)
            hex = hexlify(msgSizeEncoded)
            msgSize = int(hex, 16)
            
            # Get the message
            dataLeft = msgSize
            inData = ''
            while (dataLeft > 0):
                dataPart = client_sock.recv(STANDARD_RECEIVE_SIZE)
                dataLeft = dataLeft - len(dataPart)
                inData = inData + dataPart
                
            # Check for empty message
            if len(inData) > 0:
                logger.info("Received %s bytes: [%s]" % (msgSize, inData))
                
                # Save data if necessary
                if isSaving:
                    writeDataToFile(inData, directory)
                    
                # Return message back to sender
                outData = inData
            else:
                # Empty message
                logger.info("Received empty message")
                outData = "Empty message sent"
              
            # Package reply message
            returnMsg = packageMessage(outData)
            
            # Send reply message
            client_sock.send(returnMsg['packagedMessage'])
            logger.info("Sending: %s" % returnMsg['msg'])
        except IOError:
            pass
            
        except (ValueError, TypeError):
            logger.info("Incoming message doesn't follow the protocol.")

        except KeyboardInterrupt:
            # Stop server
            logger.info("Disconnected")

            client_sock.close()
            server_sock.close()
            logger.info("All done")

            break

            
# Create a message that conforms to the protocol
def packageMessage (message):
    # Get the message length
    msgLength = len(message)
    
    # Get the message length in hex format of appropriate length
    numHexDigits = MESSAGE_SIZE_BYTES * 2
    data = format(msgLength, '0' + str(numHexDigits) + 'X')
    
    logger.debug("Message has length %d. Encoded as %s" % (msgLength, data))
    
    # Return the packaged message
    return {'packagedMessage':unhexlify(data) + message, 'msgLength':msgLength ,'msg':message}

    
# Write data to a file in dir
def writeDataToFile(data, dir):
    # Get a file to write into
    file = getFileName(dir)
    
    # Make sure path exists
    if not os.path.exists(dir):
        os.makedirs(dir)
    
    # Write data into the file
    logger.debug("Writing data to %s" % file)
    with open(file, 'w') as f:
        f.write(data)
        logger.debug("Successfully wrote to file")

    
# Run the server with default settings
def runDefault():
    runServer(saveToDir, True)

# Run the server with custom settings    
def run(dir, isSaving):
    runServer(dir, isSaving)


# Get the correct logging level from user input
def getLoggingLevel(input):
    DEFAULT = logging.INFO
    inputLower = str(input).lower()
    
    if inputLower == "critical":
        return logging.CRITICAL
    elif inputLower == "error":
        return logging.ERROR
    elif inputLower == "warning":
        return logging.WARNING
    elif inputLower == "info":
        return logging.INFO
    elif inputLower == "debug":
        return logging.DEBUG
    elif str(input).isdigit()  == True:
        return int(input)
    else:
        return DEFAULT

        
        
# Find a file in directory that doesn't exist (for writing into)
def getFileName(directory):
    haveFile = False
    
    # Loop while we haven't found one
    while not haveFile:
        # Get a random number
        randNum = hexlify(os.urandom(4))
        
        # Create file name based on date, time, and the random number
        fileName = "receivedData-" + time.strftime("%m_%d_%y-%H_%M_%S") + "-" + randNum + ".dat"
        
        # Check if the file already exists
        if (os.path.isfile(directory + "/" + fileName)):
            # File already exists - try again
            logger.debug("%s is already a file" % fileName)
        else:
            # File doesn't exist - use it
            logger.info("Saving into file %s" % fileName)
            haveFile = True
    
    return directory + "/" + fileName
    
    
    
def setupLogging(level):
    global logger
    # Create the logger
    FORMAT = '%(levelname)s %(asctime)-15s: %(message)s'    
    logging.basicConfig(stream=sys.stdout, level=getLoggingLevel(level), format=FORMAT)
    
    # Create a file handler to output log to file
    fh = logging.FileHandler(logFile)
    fh.setLevel(level)
    fh.setFormatter(logging.Formatter(FORMAT))
    
    # Create the logger and attach file output
    logger = logging.getLogger("main")
    logger.addHandler(fh)
    logger.debug("Logger setup to %s" % level)


    
# Main function (called from command line)
if __name__ == "__main__":
    # Get command line arguments
    if len(sys.argv) > 3:
        logLevel = getLoggingLevel(sys.argv[3])
    if len(sys.argv) > 2:
        saveToDir = sys.argv[2]
    if len(sys.argv) > 1:
        saveFiles = str(sys.argv[1]).lower() == "true"
        
    # Setup logging
    setupLogging(logLevel)
    
    # Show help message
    if len(sys.argv) > 1 and sys.argv[1] == "help":
        logger.info("Usage: server.py [saveFiles saveDirectory loggingLevel]")
        logger.info("          saveFile - True/False indicating whether to save incoming data in files (default: True)")
        logger.info("     saveDirectory - Absolute or relative path to directory for saving files (default: .)")
        logger.info("      loggingLevel - The level to set logging to (e.g., info, debug, etc.) (default: info)")
        logger.info("All of these are optional. However, if you use any of them, you must use the preceding ones, too")
        logger.info("Example usage: server.py")
        logger.info("               server.py false")
        logger.info("               server.py true ./tmp")
        logger.info("               server.py true ./tmp debug")
        exit()
        
    # Run the server
    run(saveToDir, saveFiles)