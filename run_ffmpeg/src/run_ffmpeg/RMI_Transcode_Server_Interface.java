package run_ffmpeg;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RMI_Transcode_Server_Interface extends Remote
{
	public boolean transcodeFilePart( final String fileNameWithPath, final int startTime, final int frameDuration ) throws RemoteException ;
}
