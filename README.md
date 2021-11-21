# Development and Usage

See following guidelines:

- [uuAppg01Devkit Documentation](https://uuapp.plus4u.net/uu-bookkit-maing01/e884539c8511447a977c7ff070e7f2cf/book)
- [uuSubApp Instance Descriptor](https://uuapp.plus4u.net/uu-bookkit-maing01/289fcd2e11d34f3e9b2184bedb236ded/book/page?code=uuSubAppInstanceDescriptor)
- [uuApp Server Project (Java)](https://uuapp.plus4u.net/uu-bookkit-maing01/99c939a08e0849c68df5ee339c94054b/book/page?code=uuAppStyleGuide_00)
- [uuApp Client Project (UU5)](https://uuapp.plus4u.net/uu-bookkit-maing01/ed11ec379073476db0aa295ad6c00178/book/page?code=getStartedHooks)


- běží na http://localhost:8080/uu-game-maing01/22222222222222222222222222222222/
- přidán docker s databází (volitelné)
- přidán gradle wrapper 5.1.1

## demo

![Ukázka hry](demo2.gif)

## Inicializace
V insomnii a v application.properties upravit údaje a `asidLicenseOwnerList`, `awidLicenseOwner` a `token`.
Token je na https://uuidentity.plus4u.net/. 

Zavolat uuCmds nad běžící aplikací:
- sys/uuSubAppInstance/init
- sys/uuAppWorkspace/create
- sys/uuAppWorkspace/init
- aplikace běží na http://localhost:8080/uu-game-maing01/22222222222222222222222222222222/

Pro vývoj GUI je potřeba ještě spustit `npm start` v adresáři `*-hi`. 

## dtoOut

```json
{
  "uuAppErrorMap": {},
  "output": {
    "tick": 3,
    "state": "RUNNING",
    "params": {},
    "players": [],
    "game": {
      "players": {
        "\"22-2061-1\"": {
          "x": 421,
          "y": 232,
          "width": 30,
          "height": 30,
          "speed": 10,
          "ammoList": [
            {
              "type": "bullet",
              "amount": 4
            },
            {
              "type": "mine",
              "amount": 1
            }
          ],
          "lives": 1
        }
      },
      "obstacles": [
        {
          "type": "TREE",
          "walls": [
            {
              "x": 106,
              "y": 135,
              "width": 30,
              "height": 30
            }
          ]
        },
        {
          "type": "TREE",
          "walls": [
            {
              "x": 224,
              "y": 275,
              "width": 30,
              "height": 30
            }
          ]
        },
        {
          "type": "TREE",
          "walls": [
            {
              "x": 433,
              "y": 301,
              "width": 30,
              "height": 30
            }
          ]
        },
        {
          "type": "MUSHROOM",
          "walls": [
            {
              "x": 0,
              "y": 0,
              "width": 100,
              "height": 20
            },
            {
              "x": 0,
              "y": 100,
              "width": 100,
              "height": 20
            }
          ]
        }
      ],
      "ammo": []
    }
  },
  "eventType": "GameEvent"
}
```


Hudba: https://gamesounds.xyz/?dir=OpenBundle/Background%20Music%20and%20Loops

Assety: https://opengameart.org/ & https://remos.itch.io/timbered-house
