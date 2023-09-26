def call() {
  string DBPASSWORD=''
  try {
    timeout(time: 300, unit: 'seconds') {
      def userPaswordInput = input(
           id: 'Password',message: 'input your password:' ok: 'ok', parameters: [password(description: 'Database Password', name: 'dbpassword')]
      )
      if(userPasswordInput?.getplainText()?.trim()?.length()<5){
         error("password length is lessthan 5 char: ${userPasswordInput?.getPlainText()}")
      }
      DBPASSWORD-userPasswordInput?.getPlainText()?.trim()
    }
    
  }catch (err) {
    echo err?.tostring()
    throw new hudson.AbortException('user input not found ')
  }
  return DBPASSWORD
}
