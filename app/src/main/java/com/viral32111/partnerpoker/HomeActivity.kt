package com.viral32111.partnerpoker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class HomeActivity : AppCompatActivity() {
	private val logTag: String = "Partner Poker"
	@Suppress( "SpellCheckingInspection" ) private val webApplicationClientID: String = "771443298182-gdsi68k8hbvt3j56kibuslnbvo4urbsm.apps.googleusercontent.com"

	private lateinit var userTextView: TextView
	private lateinit var signOutButton: Button

	private lateinit var oneTapClient: SignInClient
	private lateinit var firebaseAuthentication: FirebaseAuth

	private lateinit var resultLauncher: ActivityResultLauncher<IntentSenderRequest>

	override fun onCreate( savedInstanceState: Bundle? ) {
		super.onCreate( savedInstanceState )
		setContentView( R.layout.activity_home )

		userTextView = findViewById( R.id.activityHomeTextViewUser )
		signOutButton = findViewById( R.id.activityHomeButtonSignOut )

		oneTapClient = Identity.getSignInClient( this )

		firebaseAuthentication = Firebase.auth
		firebaseAuthentication.addAuthStateListener { authState ->
			Log.d( logTag, "Authentication status changed! Updating user information on UI..." )

			val user = authState.currentUser

			if ( user != null ) {
				Log.d( logTag, "User Identifier: '${ user.uid }'" )
				Log.d( logTag, "Display Name: '${ user.displayName ?: "Unknown" }'" )
				Log.d( logTag, "Email Address: '${ user.email ?: "Unknown" }'" )
				Log.d( logTag, "Phone Number: '${ user.phoneNumber ?: "Unknown" }'" )
				Log.d( logTag, "Photo URL: '${ user.photoUrl ?: "Unknown" }'" )
			} else {
				Log.d( logTag, "The current user is null!" )
			}

			userTextView.text = getString( R.string.activity_home_textview_user, user?.displayName ?: "Unknown Name", user?.uid ?: "Unknown Identifier" )
		}

		resultLauncher = registerForActivityResult( ActivityResultContracts.StartIntentSenderForResult() ) { activityResult ->
			Log.d( logTag, "Got activity result" )

			if ( activityResult.resultCode == Activity.RESULT_OK ) {
				Log.d( logTag, "Activity result is okay" )

				val googleCredential = oneTapClient.getSignInCredentialFromIntent( activityResult.data )
				val idToken = googleCredential.googleIdToken

				when {
					idToken != null -> {
						// Got an ID token from Google. Use it to authenticate with Firebase.
						val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
						firebaseAuthentication.signInWithCredential(firebaseCredential)
							.addOnCompleteListener( this ) { task ->
								if ( task.isSuccessful ) {
									// Sign in success, update UI with the signed-in user's information
									Log.d( logTag, "Successfully signed in!" )
								} else {
									// If sign in fails, display a message to the user.
									Log.w( logTag, "Failed to sign in: ${ task.exception?.localizedMessage }" )
								}
							}
					}

					else -> {
						// Shouldn't happen.
						Log.d( logTag, "No ID token!" )
					}
				}
			} else {
				Log.d( logTag, "Activity result is NOT okay: ${ activityResult.resultCode }" )
			}
		}
	}

	/*override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent? ) {
		super.onActivityResult( requestCode, resultCode, data )

		Log.d( logTag, "onActivityResult() called!" )

		when ( requestCode ) {
			2 -> {
				try {

					val googleCredential = oneTapClient.getSignInCredentialFromIntent(data)
					val idToken = googleCredential.googleIdToken

					when {
						idToken != null -> {
							// Got an ID token from Google. Use it to authenticate with Firebase.
							val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
							firebaseAuthentication.signInWithCredential(firebaseCredential)
								.addOnCompleteListener( this ) { task ->
									if ( task.isSuccessful ) {
										// Sign in success, update UI with the signed-in user's information
										Log.d( logTag, "Successfully signed in!" )
									} else {
										// If sign in fails, display a message to the user.
										Log.w( logTag, "Failed to sign in: ${ task.exception?.localizedMessage }" )
									}
								}
						}

						else -> {
							// Shouldn't happen.
							Log.d( logTag, "No ID token!" )
						}
					}

				} catch (e: ApiException) {
					Log.e( logTag, e.localizedMessage ?: "API Exception?" )
				}
			}
		}
	}*/

	fun onSignInClick() {
		Log.d( logTag, "Sign in button clicked" )

		if ( firebaseAuthentication.currentUser == null ) {
			showOneTapSignIn()
		} else {
			val dialog = AlertDialog.Builder( this )
				.setTitle( "Error" )
				.setMessage( "You are already signed in!" )
				.setNegativeButton( "OK" ) { _, _ ->
					Log.d( logTag, "Negative button on dialog clicked" )
				}
				.create()

			dialog.show()
		}
	}

	fun onSignOutClick() {
		Log.d( logTag, "Sign out button clicked" )

		if ( firebaseAuthentication.currentUser != null ) {
			oneTapClient.signOut()
				.addOnSuccessListener {
					Log.d( logTag, "User has been signed out" )

					//updateUserInformation( firebaseAuthentication.currentUser )
				}
				.addOnFailureListener { exception ->
					Log.e( logTag, "Failed to sign out the user: '${ exception.localizedMessage }'" )
				}
			firebaseAuthentication.signOut()
		} else {
			val dialog = AlertDialog.Builder( this )
				.setTitle( "Error" )
				.setMessage( "You are not signed in!" )
				.setNegativeButton( "OK" ) { _, _ ->
					Log.d( logTag, "Negative button on dialog clicked" )
				}
				.create()

			dialog.show()
		}
	}

	private fun showOneTapSignIn() {
		val signInNonce = generateRandomString( 10 )
		Log.d( logTag, "Generated a random sign-in nonce: '${ signInNonce }'" )

		val signInRequest = BeginSignInRequest.builder()
			.setPasswordRequestOptions(
				BeginSignInRequest.PasswordRequestOptions.builder()
					.setSupported( true )
					.build()
			)

			.setGoogleIdTokenRequestOptions(
				BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
					.setSupported( true )
					.setServerClientId( webApplicationClientID ) // Your server's client ID, not your Android client ID
					.setFilterByAuthorizedAccounts( false ) // Do not only show accounts previously used to sign in
					.setNonce( signInNonce )
					.build()
			)
			.setAutoSelectEnabled( false ) // Do not automatically sign-in when exactly one credential is retrieved
			.build()

		Log.d( logTag, "Showing sign-in..." )
		oneTapClient.beginSignIn( signInRequest )
			.addOnSuccessListener( this ) { signInResult ->
				Log.d( logTag, "Sign-in success: ${ signInResult.pendingIntent }" )

				//val intent = Intent( this, HomeActivity::class.java )
				//activityResulter.launch( signInResult.pendingIntent )

				val senderRequest = IntentSenderRequest.Builder( signInResult.pendingIntent ).build()
				resultLauncher.launch( senderRequest )

				/*try {
					startIntentSenderForResult( signInResult.pendingIntent.intentSender, 2, null, 0, 0, 0, null )
				} catch ( e: IntentSender.SendIntentException ) {
					Log.e( logTag, "Couldn't start One Tap UI: ${ e.localizedMessage } ")
				}*/
			}
			.addOnFailureListener( this ) { exception ->
				// No saved credentials found. Launch the One Tap sign-up flow, or do nothing and continue presenting the signed-out UI.
				Log.e( logTag, "Sign-in failure: ${ exception.localizedMessage ?: "No saved credentials found?" }" )
			}
	}

	private fun generateRandomString( length: Int ) : String {
		@Suppress( "SpellCheckingInspection" ) val characters = "ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz0123456789"
		return ( 1 .. length ).map { characters.random() }.joinToString( "" )
	}

}
