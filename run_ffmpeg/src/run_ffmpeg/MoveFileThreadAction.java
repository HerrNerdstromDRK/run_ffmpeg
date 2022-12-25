package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MoveFileThreadAction extends ThreadAction
{

	String sourceFileName = null ;
	String destinationFileName = null ;

	public boolean isTestMode()
	{
		return run_ffmpeg.testMode ;
	}

	public MoveFileThreadAction( String sourceFileName, String destinationFileName )
	{
		this.sourceFileName = sourceFileName ;
		this.destinationFileName = destinationFileName ;
	}

	@Override
	public void doAction()
	{
		out( "MoveFileThreadAction.doAction> Moving file from \""
				+ sourceFileName
				+ "\" to \""
				+ destinationFileName
				+ "\"" ) ;

		File sourceFile = new File( sourceFileName ) ;
		Path sourcePath = Paths.get( sourceFile.getParent() ) ;

		File destinationFile = new File( destinationFileName ) ;
		Path destinationPath = Paths.get( destinationFile.getParent() ) ;

		try
		{
			if( !Files.exists( sourcePath ) )
			{
				out( "MoveFileThreadAction.doAction> sourcePath does not exist: " + sourcePath ) ;
				if( !isTestMode() )
				{
					Files.createDirectory( sourcePath ) ;
				}
			}

			if( !Files.exists( destinationPath ) )
			{
				out( "MoveFileThreadAction.doAction> destinationPath does not exist: " + destinationPath ) ;
				if( !isTestMode() )
				{
					Files.createDirectory( destinationPath ) ;
				}
			}

			if( !isTestMode() )
			{
				Path temp = Files.move(
						Paths.get( sourceFileName ),
						Paths.get( destinationFileName ) ) ;
				if( temp != null )
				{
					out( "MoveFileThreadAction.doAction> Success" ) ;

				}
				else
				{
					out( "MoveFileThreadAction.doAction> Failed" ) ;
				}
			}
		}
		catch( Exception theException )
		{
			out( "MoveFileThreadAction.doAction> Exception: " + theException.toString() ) ;
		}
	}

	public static void out( final String writeMe )
	{
		run_ffmpeg.out( writeMe ) ;
	}

	@Override
	public String toString()
	{
		String retMe = "MoveFileThreadAction> Move file from \""
				+ sourceFileName
				+ "\" to \""
				+ destinationFileName
				+ "\"";
		return retMe ;
	}

}
