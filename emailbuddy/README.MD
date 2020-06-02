carga de usuario:
- por CSV, de cada usuario tengo las preguntas del cuestionario (1 a 5) y genero la personality //api done
- por ui puedo cargar 1 usuario (pongo sliders para cada personality trait)  // Done

crear campaña {

elegir base de usuarios

cargo n templates:
    agarro un sample de usuarios para cada template de dsitintas personalities
    y mando
    cuando responden, correlaciono cada template a un tipo de personality distinto, 
    y habilito opcion para mandar los mails con template acorde a cada persona

}

generate Mail {
el mail es un template que recibe el usuario (para persoanlizar) y un userID  unico para el tracking pixel y la url
tracking pixel = <img src=http://foo:8080/userId>
}

apis expuestas:
imgApi:  pixel/${userId}/pixel.jpg  // registers MailOpened (with date.now()), returns pixel.jpg
siteOpened: siteOpened/${userId}  // registers SiteOpened, returns nothing
codeUsed: codeUsed/${userId} // registers CodeUsed when referal link is used, returns nothing       
createCampaign: campaign // { nombre, baseId, [templates] } solo crea, no ejecuta nada              // Done
runSampling :   //campaignId, sampling percentage                                                   //
runFullCampaign: // campaignId                                                                      //
userBase:  CRUD
user: CRUD
template: CRUD

reporting :
getCampaignStatus: // campaignId => totalUsers, State



User( 
name: String,
email: String,
sex: Sex,
age: Int,
hand: Handedness,
nationality: String
personality: Personality)

Personality(
extraversion: Int
agreeableness: Int
conscientiousness: Int
neuroticism: Int
opennes: Int
)

UserTracking(
state: State
date: Date
)
 
 State [MailOpened | SiteOpened | CodeUsed ]



- create user base from csv.
- sample
- collect events