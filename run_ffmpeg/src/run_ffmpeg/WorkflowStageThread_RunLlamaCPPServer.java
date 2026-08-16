package run_ffmpeg;

import java.io.BufferedReader;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

public class WorkflowStageThread_RunLlamaCPPServer extends WorkflowStageThread
{
	
	protected Process theProcess = null ;
	protected BufferedReader inputStreamReader = null ;
	
	public WorkflowStageThread_RunLlamaCPPServer( final String threadName, Logger log, Common common, MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
		startLlamaCPPServer() ;
	}
	
	protected void startLlamaCPPServer()
	{
		ImmutableList.Builder< String > llamaCPPServerExecuteCommand = new ImmutableList.Builder<String>() ;
		llamaCPPServerExecuteCommand.add( common.getPathToLlamaCPPServer() ) ;
		llamaCPPServerExecuteCommand.add( "-hf", "ggml-org/GLM-OCR-GGUF:F16" ) ;
		llamaCPPServerExecuteCommand.add( "--parallel", "12" ) ;
		
		try
		{
			Thread.currentThread().setPriority( Thread.MIN_PRIORITY ) ;
			final ProcessBuilder theProcessBuilder = new ProcessBuilder( llamaCPPServerExecuteCommand.build().toArray( new String[ 1 ] ) ) ;
			theProcessBuilder.redirectErrorStream( true ) ;
			
			theProcess = theProcessBuilder.start() ;
			inputStreamReader = theProcess.inputReader() ;
			
			String inputStreamLine = null ;
			String lastInputStreamLine = "" ; // never null
			
			// Keep reading until I find the "listening on" message indicating that the server is running
			while( (inputStreamLine = inputStreamReader.readLine()) != null )
			{
				if( inputStreamLine.equalsIgnoreCase( lastInputStreamLine ) )
				{
					// Same as last input
					continue ;
				}
				lastInputStreamLine = inputStreamLine ;
				
				log.fine( inputStreamLine ) ;

				if( inputStreamLine.contains( "listening on" ) )
				{
					// Server is now running
					log.info( "llamaCPPServer running" ) ;
					break ;
				}
				
			} // while( readLine() )
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException + " for command: " + llamaCPPServerExecuteCommand ) ;
		}
	}

	/**
	 * Override this method. This method performs the work intended for subclass instances of this class.
	 * @return true if work completed false if no work completed.
	 */
	public boolean doAction()
	{
		try
		{
			if( !inputStreamReader.ready() )
			{
				setWorkInProgress( false ) ;
				return false ;
			}
			
			// Read as many lines as are ready
			while( inputStreamReader.ready() )
			{
				inputStreamReader.readLine() ;
				setWorkInProgress( true ) ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
			return false ;
		}
		
		return true ;
	}

	@Override
	public String getUpdateString()
	{
		return "" ;
	}
	
	public void stopRunning()
	{
		try
		{
			theProcess.destroy() ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
			theException.printStackTrace() ;
		}
		super.stopRunning() ;
	}
}
