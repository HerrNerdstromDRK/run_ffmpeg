package run_ffmpeg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * A worker thread to move files.
 * @author Dan
 *
 */
public class MoveFilesWorkerThread extends Thread
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// Work queue
	protected List< MoveFileInfo > moveActionList = null ;

	/// File name to which to log activities for this application.
	//	private final String logFileName = "log_move_files_thread.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_move_files_thread.txt" ;

	/// Variable the controller thread can use to shutdown the thread.
	protected boolean doKeepRunning = true ;

	/// The name of this thread.
	protected String name = "" ;

	public MoveFilesWorkerThread( Logger log, Common common, List< MoveFileInfo > moveActionList, final String name )
	{
		this.log = log ;
		this.common = common ;
		this.moveActionList = moveActionList ;
		this.name = name ;
	}

	public void addWork( final String inputFileNameWithPath, final String destinationFileNameWithPath )
	{
		MoveFileInfo moveFileInfo = new MoveFileInfo( inputFileNameWithPath, destinationFileNameWithPath ) ;
		synchronized( moveActionList )
		{
			moveActionList.add( moveFileInfo ) ;
		}
	}

	@Override
	public void run()
	{
		while( shouldKeepRunning() )
		{
			MoveFileInfo moveInfo = getNextWorkItem() ;
			try
			{
				if( null == moveInfo )
				{
					Thread.sleep( 100 ) ;
					continue ;
				}

				if( !Files.exists( moveInfo.getSourceFileDirectoryPath() ) )
				{
					log.info( "Creating sourceFileDirectoryPath: " + moveInfo.getSourceFileDirectoryPath()
					+ " " + toString() ) ;
					if( !common.getTestMode() )
					{
						Files.createDirectory( moveInfo.getSourceFileDirectoryPath() ) ;
					}
				}

				if( !Files.exists( moveInfo.getDestinationFileDirectoryPath() ) )
				{
					log.info( "Creating destinationFileDirectoryPath: " + moveInfo.getDestinationFileDirectoryPath()
					+ " " + toString() ) ;
					if( !common.getTestMode())
					{
						Files.createDirectory( moveInfo.getDestinationFileDirectoryPath() ) ;
					}
				}

				log.info( "Moving " + moveInfo.getSourceFilePath().toString()
						+ " -> " + moveInfo.getDestinationFilePath().toString() ) ;
				if( !common.getTestMode() )
				{
					final long startTime = System.nanoTime() ;
					Path temp = Files.move(	moveInfo.getSourceFilePath(), moveInfo.getDestinationFilePath() ) ;
					if( temp != null )
					{
						final long endTime = System.nanoTime() ;
						final double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;
						final long fileLength = moveInfo.getDestinationFile().length() ;
						final double fileLengthInMB = fileLength / 1e6 ;
						final double MBPerSecond = fileLengthInMB / timeElapsedInSeconds ;

						log.info( "Successfully moved "
								+ moveInfo.getSourceFileNameWithPath() 
								+ " -> "
								+ moveInfo.getDestinationFileNameWithPath()
								+ "; elapsed time: "
								+ common.getNumberFormat().format( timeElapsedInSeconds )
								+ " seconds, "
								+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
								+ " minutes; moved " + fileLengthInMB + "MB at "
								+ common.getNumberFormat().format( MBPerSecond ) + "MB/sec"
								+ " " + toString() ) ;
					}
					else
					{
						log.warning( "Failed: " + toString() ) ;
					}
				}
			}
			catch( Exception theException )
			{
				log.warning( "Exception: " + theException.toString() ) ;
				return ;
			}
		} // while( keepRunning )
	}

	protected MoveFileInfo getNextWorkItem()
	{
		MoveFileInfo retMe = null ;
		synchronized( moveActionList )
		{
			if( !moveActionList.isEmpty() )
			{
				retMe = moveActionList.remove( 0 ) ;
			}
		}
		return retMe ;
	}

	protected boolean hasMoreWork()
	{
		boolean retMe = false ;
		synchronized( moveActionList )
		{
			retMe = !moveActionList.isEmpty() ;
		}
		return retMe ;
	}

	public String getStopFileName()
	{
		return stopFileName;
	}

	public boolean shouldKeepRunning()
	{
		return (!common.shouldStopExecution( getStopFileName() ) && doKeepRunning) ;
	}

	/**
	 * Stop the thread from executing. Note that this will abandon any remaining work items.
	 */
	public void stopRunning()
	{
		doKeepRunning = false ;
	}

	public String toString()
	{
		return name ;
	}
}
