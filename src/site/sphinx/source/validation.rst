======================================
読み込んだ値の入力値検証
======================================

--------------------------------------------------------
入力値検証の基本
--------------------------------------------------------


 XlsMapperの入力値検証は、SpringFrameworkのValidation機構に似た方法をとります。
 
* エラー情報は、SheetBindingErrorsクラスに格納します。

    * SheetBindingErrorsのインスタンスを、読み込み時に引数として渡します。

* セルの値をJavaオブジェクトに変換の失敗情報は、読み込み時に自動的に作成されます。

    * 1つのセルの型変換に失敗しても処理を続行するよう、システム設定XlsMapperConfigの項目「skipTypeBindFailure」を'true'に設定します。

* 別途用意したBeanに対するValidatorにより、値を検証します。

   * 通常は、抽象クラス「AbstractObjectValidator」を継承して作成します。
   * ``@XlsHorizontalRecords`` のようにネストしたBeanの場合、リストの要素のBeanのValidatorを別途用意します。

* エラーがある場合、SheetMessageConverterを使用して、エラーオブジェクトを文字列に変換します。


.. sourcecode:: java
    
    XlsMapper xlsMapper = new XlsMapper();
    
    // 型変換エラーが起きても処理を続行するよう設定
    xlsMapper.getConig().setSkipTypeBindFailure(true);
    
    // エラー情報の管理クラスのインスタンスの作成。オブジェクト名をクラス名に設定します。
    SheetBindingErrors errors = new SheetBindingErrors(Employer.class);
    
    // エラー情報を読み込み時に渡し、読み込み処理を実行します。
    Employer bean = xlsMapper.load(xlsIn, Employer.class, errors);
    
    // BeanのValidatorを実行します。
    EmployerValidator validator = new EmployerValidator();
    validator.validate(bean, errors);
    
    // 値の検証結果を文字列に変換します。
    if(errors.hasErrors()) {
        SheetMessageConverter messageConverter = new SheetMessageConverter();
        for(ObjectError error : errors.getAllErrors()) {
            String message = messageConverter.convertMessage(error);
        }
    }

--------------------------------------------------------
独自の入力値検証
--------------------------------------------------------


Validatorは、AbstractObjectValidatorを継承して作成します。

* Genericsで検証対象のBeanクラスを指定し、validateメソッド内で検証処理の実装を行います。
* 検証対象のフィールドにエラーがあるかどうかは、SheetBindingErrors#hasFieldErrorsでチェックできます。
    
    * 型変換エラーがある場合、Beanに値がマッピングされていないため、エラーがあるかどうかをチェックします。
    
* フィールドに対するエラーを設定する場合、``SheetBindingErrors#rejectValue("フィールド名", "エラーコード", "エラー引数")`` で設定します。
    
    * Map<String, Points>フィールドでセルのアドレスを保持している場合は、``SheetBindingErrors#rejectSheetValue(...)`` でセルのアドレスを指定できます。
    
    * エラー引数は、インデックス形式の配列型と名前付きのマップ型のどちらでも指定できます。名前付きのマップ型の利用をお勧めします。
    

.. sourcecode:: java
    
    public class EmployerValidator extends AbstractObjectValidator<Employer>{
        
        // ネストしたBeanのValidator
        private EmployerHistoryValidator historyValidator;
        
        public EmployerValidator() {
            this.historyValidator = new EmployerHistoryValidator();
        }
        
        @Override
        public void validate(final Employer targetObj, final SheetBindingErrors errors) {
            
            // 型変換などのエラーがない場合、文字長のチェックを行う
            // チェック対象のフィールド名を指定します。
            if(!errors.hasFieldErrors("name")) {
                if(targetObj.getName().length() > 10) {
                    // インデックス形式のメッセージ引数の指定
                    //errors.rejectValue("name", "error.maxLength", new Object[]{10});
                    
                    // 名前付きの引数、セルのアドレスを渡す場合の指定
                    Map<String, Object> vars = new HashMap<>();
                    Point address = targetObj.positions("name");
                    errors.rejectSheetValue("name", address, "error.maxLength", vars);
                }
            }
            
            for(int i=0; i < targetObj.getHistory().size(); i++) {
                // ネストしたBeanの検証の実行
                // パスをネストする。リストの場合はインデックスを指定する。
                errors.pushNestedPath("history", i);
                historyValidator.validate(targetObj.getHistory().get(i), errors);
                // 検証後は、パスを戻す
                errors.popNestedPath();
                
                // パスのネストと戻しは、invokeNestedValidatorで自動的にもできます。
                // invokeNestedValidator(historyValidator, targetObj.getHistory().get(i), errors, "history", i);
            }
            
        }
    }



--------------------------------------------------------
フィールド（プロパティ）の入力値検証
--------------------------------------------------------

フィールドに対する値の検証は、CellFieldクラスを使用することでもできます。

* フィールドに対する検証をCellField#add(...)で追加することで複数の検証を設定できます。
* 値の件所を行う場合は、CellField#validate(errors)で実行します。

   * SheetBindingErrorsに対してエラーオブジェクトが自動的に設定されます。
   
* フィールドに対してエラーがある場合、CellField#hasErrors(...)/hasNotErrors(...)で検証できます。
 

.. sourcecode:: java
    
    public class EmployerHistoryValidator extends AbstractObjectValidator<EmployerHistory>{
        
        @Override
        public void validate(final EmployerHistory targetObj, final SheetBindingErrors errors) {
            
            final CellField<Date> historyDateField = new CellField<Date>(targetObj, "historyDate");
            historyDateField.setRequired(true)
                .add(new MinValidator<Date>(new Date(), "yyyy-MM-dd"))
                .validate(errors);
            
            
            final CellField<String> commentField = new CellField<String>(targetObj, "comment");
            commentField.setRequired(false)
                .add(StringValidator.maxLength(5))
                .validate(errors);
            
            if(historyDateField.hasNotErrors(errors) && commentField.hasNotErrors(errors)) {
                // 項目間のチェックなど
                if(commentField.isInputEmpty()) {
                    errors.reject("error.01");
                }
            }
            
        }
    }


--------------------------------------------------------
メッセージファイルの定義
--------------------------------------------------------


メッセージファイルは、クラスパスのルートに「SheetValidationMessages.properties」というプロパティファイルを配置しておくと、自動的に読み込まれます。
 
* 型変換エラーは、読み込み時に自動的にチェックされ、エラーコードは、「cellTypeMismatch」と決まっています。
 
   * フィールドのクラスタイプごとに、メッセージを指定することもでき、「cellTypeMismatch.\<クラス名\>」で定義します。
   * さらに、フィールド名でも指定することができ、「cellTypeMismatch.\<フィールド名\>」で定義します。
   * クラスタイプよりもフィールド名で指定する方が優先されます。
 
* メッセージ中ではEL式を利用することができます。
* メッセージ中の通常の変数は、\{変数名\}で定義し、EL式は$\{EL式\}で定義します。
   
   * ただし、EL式のライブラリを依存関係に追加しておく必要があります。
   

.. sourcecode:: 
    
    ## メッセージの定義
    ## SheetValidationMessages.properties
    
    # 共通変数
    # {sheetName} : シート名
    # {cellAddress} : セルのアドレス。'A1'などの形式。
    # {label} : フィールドの見出し。
    
    # 型変換エラー
    cellTypeMismatch=[{sheetName}]:${empty label ? '' : label} - {cellAddress}の型変換に失敗しました。
    
    # クラスタイプで指定する場合
    cellTypeMismatch.int=[{sheetName}]:${empty label ? '' : label} - {cellAddress}は数値型で指定してください。
    cellTypeMismatch.java.util.Date=[{sheetName}]:${empty label ? '' : label} - {cellAddress}は日付型で指定してください。
    
    # フィールド名で指定する場合
    cellTypeMismatch.updateTime=[{sheetName}]:${empty label ? '' : label} - {cellAddress}は'yyyy/MM/dd'の書式で指定してください。


--------------------------------------------------------
メッセージファイルの読み込み方法の変更
--------------------------------------------------------

メッセージファイルは、ResourceBundleやProperties、またSpringのMessageSourceからも取得できます。
設定する場合、``SheetMessageConverter#setMessageResolver(...)`` で対応するクラスを設定します。

.. list-table:: メッセージファイルのブリッジ用クラス
   :widths: 50 50
   :header-rows: 1
   
   * - XlsMapper提供のクラス
     - メッセージ取得元のクラス
   
   * - com.gh.mygreen.xlsmapper.validation.ResourceBundleMessageResolver
     - java.util.ResourceBundle
   
   * - com.gh.mygreen.xlsmapper.validation.PropertiesMessageResolver
     - java.util.Prperties
   
   * - com.gh.mygreen.xlsmapper.validation.SpringMessageResolver
     - org.springframework.context.MessageSource


.. sourcecode:: java
    
    // SpringのMessageSourceからメッセージを取得する場合
    MessageSource messageSource = /*...*/;
    
    SheetMessageConverter messageConverter = new SheetMessageConverter();
    messageConverter.setMessageResolver(new SpringMessageResolver(messageSource));


--------------------------------------------------------
EL式のカスタマイズ
--------------------------------------------------------


メッセージ中の式言語は、EL式以外も利用できます。

EL式の他、MVEL、OGNL、SpEL(Spring Expresson Language)が利用できます。

使用する式言語を変更する場合、``MessageInterapolator#setExpressionLanguage(...)`` で式言語の実装を設定します。

MVELやSpELを利用する場合、別途、ライブラリが必要になります。

.. sourcecode:: java
    
    SheetMessageConverter messageConverter = new SheetMessageConverter();
    
    // EL式の管理クラスの作成
    ExpressionLanguageRegistry elRegistry = new ExpressionLanguageRegistry();
    
    // 式言語の設定
    messageConverter.getMessageInterporlator()
        .setExpressionLanguage(elRegistry.getExpressionLanguage("mvel"));


.. note:: 
   
   式言語を変更した場合、メッセージ中の${EL式}を、言語特有のものに変更する必要があります。
   

.. sourcecode:: xml
    
    <!-- ====================== 各式言語のライブラリ ===============-->
    <!-- EL式を利用する場合 -->
    <dependency>
        <groupId>javax.el</groupId>
        <artifactId>javax.el-api</artifactId>
        <version>3.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.glassfish</groupId>
        <artifactId>javax.el</artifactId>
        <version>3.0.0</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- 式言語:OGNL -->
    <dependency>
        <groupId>ognl</groupId>
        <artifactId>ognl</artifactId>
        <version>3.0.8</version>
    </dependency>
    
    <!-- 式言語:MVEL -->
    <dependency>
        <groupId>org.mvel</groupId>
        <artifactId>mvel2</artifactId>
        <version>2.2.2.Final</version>
    </dependency>
    
    <!-- 式言語：Spring Expression Language -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-expression</artifactId>
        <version>3.2.11.RELEASE</version>
    </dependency>


--------------------------------------------------------
Bean Validationを使用した入力値検証
--------------------------------------------------------

 BeanValidation JSR-303(ver.1.0)/JSR-349(ver.1.1)を利用する場合、ライブラリで用意されている「SheetBeanValidator」を使用します。
 
* BeanValidationの実装として、Hibernate Validatorが必要になるため、依存関係に追加します。
    
    * Hibernate Validatorを利用するため、メッセージをカスタマイズしたい場合は、クラスパスのルートに「ValidationMessages.properties」を配置します。
    
* 検証する際には、SheetBeanValidator#validate(...)を実行します。
    
    * Bean Validationの検証結果も、SheetBindingErrorsの形式に変換され格納されます。
    
* メッセー時を出力する場合は、SheetMessageConverterを使用します。


.. sourcecode:: java
    
    // シートの読み込み
    SheetBindingErrors errors = new SheetBindingErrors(Employer.class);
    Employer beanObj = loadSheet(new File("./src/test/data/employer.xlsx"), errors);
    
    // Bean Validationによる検証の実行
    SheetBeanValidator validatorAdaptor = new SheetBeanValidator(validator);
    validatorAdaptor.validate(beanObj, errors);
    
    // 値の検証結果を文字列に変換します。
    if(errors.hasErrors()) {
        SheetMessageConverter messageConverter = new SheetMessageConverter();
        for(ObjectError error : errors.getAllErrors()) {
            String message = messageConverter.convertMessage(error);
        }
    }



.. sourcecode:: xml
    
    <!-- ====================== Bean Validationのライブラリ ===============-->
    <!-- Bean Validation 1.1 系を利用する -->
    <dependency>
        <groupId>javax.validation</groupId>
        <artifactId>validation-api</artifactId>
        <version>1.1.0.Final</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.hibernate</groupId>
        <artifactId>hibernate-validator</artifactId>
        <version>5.1.3.Final</version>
        <scope>provided</scope>
    </dependency>






